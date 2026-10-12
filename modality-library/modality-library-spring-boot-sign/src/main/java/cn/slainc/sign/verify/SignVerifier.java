package cn.slainc.sign.verify;

import cn.slainc.sign.SignAlgorithm;
import cn.slainc.sign.composer.SignComposer;
import cn.slainc.sign.config.SignProperties;
import cn.slainc.sign.key.SignKeyDefinition;
import cn.slainc.sign.key.SignKeyRegistry;
import cn.slainc.sign.store.MemorySignNonceStore;
import cn.slainc.sign.store.SignNonceStore;
import cn.slainc.utils.net.IpMatcher;
import cn.slainc.utils.net.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SignVerifier {

    private static final Logger log = LoggerFactory.getLogger(SignVerifier.class);

    // 大于该值视为毫秒时间戳，否则按秒处理
    private static final long MILLIS_THRESHOLD = 100_000_000_000L;

    private final SignKeyRegistry registry;

    private final SignComposer composer;

    private final SignProperties properties;

    private final SignNonceStore store;

    private final MemorySignNonceStore fallbackStore = new MemorySignNonceStore();

    public SignVerifier(SignKeyRegistry registry, SignComposer composer, SignProperties properties,
                        SignNonceStore store) {
        this.registry = registry;
        this.composer = composer;
        this.properties = properties;
        this.store = store;
    }

    public SignVerifyResult verify(String appId, String algorithm, int version, String timestamp,
                                   String nonce, String sign, Map<String, List<String>> params) {
        return verify(appId, algorithm, version, timestamp, nonce, sign, params, 0, false);
    }

    public SignVerifyResult verify(String appId, String algorithm, int version, String timestamp,
                                   String nonce, String sign, Map<String, List<String>> params,
                                   long windowSecondsOverride, boolean includeEmpty) {
        if (appId == null || appId.isBlank()) {
            return fail(null, "缺少应用标识（请求头 " + properties.getHeader().getAppId() + "）");
        }
        SignKeyDefinition key = registry.find(appId).orElse(null);
        if (key == null) {
            return fail(appId, "应用未登记：" + appId);
        }
        if (!key.isEnabled()) {
            return fail(appId, "应用已停用：" + appId);
        }
        SignAlgorithm resolved = SignAlgorithm.fromName(algorithm);
        if (resolved == null) {
            resolved = key.algorithmEnum();
        }
        if (resolved == null) {
            return fail(appId, "无法确定签名算法");
        }
        if (!registry.isAlgorithmAllowed(resolved)) {
            return fail(appId, "签名算法 " + resolved + " 不在允许清单内，已拒绝（防止算法降级攻击）");
        }
        if (version != 1 && version != 2) {
            return fail(appId, "不支持的签名版本：" + version);
        }
        if (version == 1 && !resolved.isHmac()) {
            return fail(appId, "v1 签名仅支持 HMAC 算法");
        }
        long window = windowSecondsOverride > 0 ? windowSecondsOverride : properties.getWindowSeconds();
        Long parsedTimestamp = parseTimestamp(timestamp);
        if (parsedTimestamp == null) {
            return fail(appId, "时间戳缺失或格式不正确");
        }
        long requestSeconds = parsedTimestamp >= MILLIS_THRESHOLD ? parsedTimestamp / 1000 : parsedTimestamp;
        long drift = Math.abs(Instant.now().getEpochSecond() - requestSeconds);
        if (drift > window) {
            return fail(appId, "时间戳超出允许窗口，偏差 " + drift + " 秒");
        }
        if (nonce == null || nonce.length() < properties.getMinNonceLength()) {
            return fail(appId, "随机数 nonce 缺失或长度不足（至少 " + properties.getMinNonceLength() + " 位）");
        }
        if (sign == null || sign.isBlank()) {
            return fail(appId, "缺少签名（请求头 " + properties.getHeader().getSign() + "）");
        }
        String ipRejectedReason = checkIpWhitelist(key);
        if (ipRejectedReason != null) {
            return fail(appId, ipRejectedReason);
        }
        String canonical = composer.compose(version, appId, timestamp, nonce, params, includeEmpty);
        boolean matched = false;
        try {
            matched = composer.matches(version, resolved, key, canonical, sign);
        } catch (RuntimeException exception) {
            log.warn("签名计算异常 appId={}：{}", appId, exception.getMessage());
        }
        if (!matched) {
            return fail(appId, "签名不匹配");
        }
        // 必须验签通过后再消费 nonce：否则一次签名错误的请求会烧掉 nonce，合法重试被误判为重放
        String acquireFailure = acquire(appId, nonce, Math.max(window, 1));
        if (acquireFailure != null) {
            return fail(appId, acquireFailure);
        }
        return SignVerifyResult.ok(appId);
    }

    private Long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String checkIpWhitelist(SignKeyDefinition key) {
        List<String> allowed = key.getAllowedIps();
        if (allowed == null || allowed.isEmpty()) {
            return null;
        }
        HttpServletRequest request = currentRequest();
        String requestIp = request == null ? null
                : IpUtils.resolveClientIp(request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP"),
                        request.getHeader("CF-Connecting-IP"), request.getRemoteAddr());
        if (requestIp == null || requestIp.isBlank()) {
            return "无法解析来源 IP，拒绝验签";
        }
        if (IpMatcher.matchesAny(requestIp, allowed)) {
            return null;
        }
        return "来源 IP 不在应用白名单：" + requestIp;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String acquire(String appId, String nonce, long ttlSeconds) {
        SignNonceStore target = store == null ? fallbackStore : store;
        try {
            return target.tryAcquire(appId, nonce, ttlSeconds) ? null : "nonce 已被使用，疑似重放请求，已拦截";
        } catch (RuntimeException exception) {
            if (!fallbackAllowed(appId)) {
                log.error("签名 nonce 存储不可用，该应用禁止降级为单机内存，已拒绝本次请求（appId={}）：{}",
                        appId, exception.getMessage());
                return "随机数存储不可用，已拒绝本次请求";
            }
            log.warn("签名 nonce 存储不可用，降级为本地内存（appId={}）：{}", appId, exception.getMessage());
            return fallbackStore.tryAcquire(appId, nonce, ttlSeconds) ? null : "nonce 已被使用，疑似重放请求，已拦截";
        }
    }

    private boolean fallbackAllowed(String appId) {
        if (!properties.isAllowStoreFallback()) {
            return false;
        }
        List<String> sensitive = properties.getHighSensitivityAppIds();
        return sensitive == null || sensitive.stream().noneMatch(entry -> entry != null && entry.equals(appId));
    }

    private void countFailure(String appId) {
        SignNonceStore target = store == null ? fallbackStore : store;
        try {
            target.incrementFailure(appId, 3600);
        } catch (RuntimeException exception) {
            log.warn("签名失败计数存储不可用，降级为本地内存（appId={}）：{}", appId, exception.getMessage());
            fallbackStore.incrementFailure(appId, 3600);
        }
    }

    private SignVerifyResult fail(String appId, String reason) {
        log.warn("签名校验失败：appId={}，原因：{}", appId, reason);
        if (appId != null && !appId.isBlank()) {
            countFailure(appId);
        }
        return SignVerifyResult.fail(appId, reason);
    }
}
