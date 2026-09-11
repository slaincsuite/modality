package cn.slainc.captcha;

import cn.slainc.captcha.CaptchaInput.ClickPoint;
import cn.slainc.captcha.generator.CaptchaGenerator;
import cn.slainc.captcha.store.CaptchaStore;
import cn.slainc.captcha.support.CaptchaDataCodec;
import cn.slainc.captcha.support.CaptchaKeys;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.exception.SystemException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.redis.ratelimit.RateLimiter;
import cn.slainc.redis.ratelimit.RateLimitType;
import org.springframework.dao.DataAccessException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CaptchaService {

    public static final String CONFIG_TYPE = "modality.captcha.type";
    public static final String CONFIG_LENGTH = "modality.captcha.length";
    public static final String CONFIG_TTL_SECONDS = "modality.captcha.ttl-seconds";
    public static final String CONFIG_FAIL_THRESHOLD = "modality.captcha.fail-threshold";
    public static final String CONFIG_IP_LIMIT_PER_MINUTE = "modality.captcha.ip-limit-per-minute";
    public static final String CONFIG_IP_BLACKLIST = "modality.captcha.ip-blacklist";

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final String RATE_LIMIT_IP_KEY = "captcha:generate:ip:";
    private static final String RATE_LIMIT_GLOBAL_KEY = "captcha:generate:global";

    private final CaptchaStore store;

    private final CaptchaProperties properties;

    private final CaptchaTypeResolver typeResolver;

    private final CaptchaMetrics metrics;

    private final RateLimiter rateLimiter;

    private final DynamicConfigService dynamicConfigService;

    private final CaptchaGenerator generator = new CaptchaGenerator();

    public CaptchaService(CaptchaStore store, CaptchaProperties properties, CaptchaTypeResolver typeResolver,
                          CaptchaMetrics metrics, RateLimiter rateLimiter, DynamicConfigService dynamicConfigService) {
        if (store == null) {
            throw new IllegalArgumentException("验证码存储不能为空");
        }
        if (properties == null) {
            throw new IllegalArgumentException("验证码配置不能为空");
        }
        if (typeResolver == null) {
            throw new IllegalArgumentException("验证码类型解析器不能为空");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("验证码指标不能为空");
        }
        this.store = store;
        this.properties = properties;
        this.typeResolver = typeResolver;
        this.metrics = metrics;
        this.rateLimiter = rateLimiter;
        this.dynamicConfigService = dynamicConfigService;
    }

    public CaptchaChallenge generate(String channel, CaptchaType type, CaptchaParams params, String clientIp) {
        String normalizedChannel = Channels.normalize(channel);
        CaptchaType effectiveType = type != null ? type : typeResolver.resolve();
        CaptchaParams safeParams = params == null ? CaptchaParams.none() : params;
        checkBlacklist(clientIp);
        checkRateLimit(clientIp);
        try {
            CaptchaGenerator.Generated generated = generator.generate(effectiveType, properties, safeParams, resolveLength());
            Duration ttl = resolveTtl();
            String id = UUID.randomUUID().toString().replace("-", "");
            store.put(CaptchaKeys.dataKey(normalizedChannel, id), CaptchaDataCodec.write(generated.data()), ttl);
            metrics.recordGenerated();
            return new CaptchaChallenge(id, normalizedChannel, generated.type(), generated.imageBase64(),
                    generated.sliderImageBase64(), generated.words(), generated.width(), generated.height(),
                    ttl.toSeconds());
        } catch (DataAccessException exception) {
            throw new SystemException(ResultCode.INTERNAL_SERVER_ERROR, "验证码存储服务异常", exception);
        }
    }

    public CaptchaChallenge generate(String channel, CaptchaType type, String clientIp) {
        return generate(channel, type, CaptchaParams.none(), clientIp);
    }

    public CaptchaChallenge generate(String channel, String clientIp) {
        return generate(channel, null, CaptchaParams.none(), clientIp);
    }

    public CaptchaVerifyResult verify(String channel, String id, CaptchaInput input) {
        String normalizedChannel;
        try {
            normalizedChannel = Channels.normalize(channel);
        } catch (IllegalArgumentException exception) {
            return CaptchaVerifyResult.fail(exception.getMessage());
        }
        if (id == null || id.isBlank()) {
            return CaptchaVerifyResult.fail("验证码标识不能为空");
        }
        CaptchaInput actualInput = input == null ? CaptchaInput.ofCode(null) : input;
        try {
            if (store.isLocked(CaptchaKeys.lockKey(normalizedChannel, id))) {
                metrics.recordLocked();
                return CaptchaVerifyResult.fail("验证码已锁定，请稍后重试");
            }
            String json = store.get(CaptchaKeys.dataKey(normalizedChannel, id));
            if (json == null || json.isBlank()) {
                recordFailure(normalizedChannel, id);
                return CaptchaVerifyResult.fail("验证码已过期或不存在，请重新获取");
            }
            CaptchaData data = CaptchaDataCodec.read(json);
            if (matches(data, actualInput)) {
                store.remove(CaptchaKeys.dataKey(normalizedChannel, id));
                metrics.recordVerifySuccess();
                return CaptchaVerifyResult.ok();
            }
            recordFailure(normalizedChannel, id);
            return CaptchaVerifyResult.fail(failureMessage(data.type()));
        } catch (DataAccessException exception) {
            throw new SystemException(ResultCode.INTERNAL_SERVER_ERROR, "验证码存储服务异常", exception);
        } catch (RuntimeException exception) {
            return CaptchaVerifyResult.fail("验证码校验失败，请稍后重试");
        }
    }

    public CaptchaMetrics getMetrics() {
        return metrics;
    }

    private boolean matches(CaptchaData data, CaptchaInput input) {
        return switch (data.type()) {
            case CHAR_NUMBER, CHAR_ALPHA, CHAR_ALNUM, ARITHMETIC -> textMatches(data.answer(), input.code());
            case CHINESE -> chineseMatches(data.answer(), input.code());
            case SLIDE -> input.slideX() != null && data.slideX() != null
                    && Math.abs(input.slideX() - data.slideX()) <= data.tolerance();
            case CLICK -> clickMatches(data.points(), input.points(), data.tolerance());
        };
    }

    private boolean textMatches(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }

    private boolean chineseMatches(String expected, String actual) {
        return expected != null && actual != null && expected.equals(actual.trim());
    }

    private boolean clickMatches(int[][] expected, List<ClickPoint> actual, int tolerance) {
        if (expected == null || expected.length == 0 || actual == null || actual.size() != expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            ClickPoint point = actual.get(i);
            if (point == null) {
                return false;
            }
            long dx = point.x() - expected[i][0];
            long dy = point.y() - expected[i][1];
            if (dx * dx + dy * dy > (long) tolerance * tolerance) {
                return false;
            }
        }
        return true;
    }

    private void recordFailure(String channel, String id) {
        store.remove(CaptchaKeys.dataKey(channel, id));
        metrics.recordVerifyFailure();
        long threshold = resolveFailThreshold();
        long count = store.incrementFail(CaptchaKeys.failKey(channel, id), resolveTtl());
        if (threshold > 0 && count >= threshold) {
            store.lock(CaptchaKeys.lockKey(channel, id), resolveTtl());
        }
    }

    private String failureMessage(CaptchaType type) {
        return switch (type) {
            case SLIDE -> "滑动位置不正确，请重试";
            case CLICK -> "点选位置或顺序不正确，请重试";
            default -> "验证码错误";
        };
    }

    private void checkBlacklist(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        String trimmed = clientIp.trim();
        boolean blocked = blacklist().stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .anyMatch(entry -> entry.equalsIgnoreCase(trimmed));
        if (blocked) {
            throw new BizException(ResultCode.FORBIDDEN, "客户端 IP 已被加入验证码黑名单");
        }
    }

    private List<String> blacklist() {
        if (dynamicConfigService != null) {
            String dynamic = dynamicConfigService.getString(CONFIG_IP_BLACKLIST, null);
            if (dynamic != null && !dynamic.isBlank()) {
                return Arrays.stream(dynamic.split("[,，;；]"))
                        .map(String::trim)
                        .filter(entry -> !entry.isEmpty())
                        .toList();
            }
        }
        return properties.getIpBlacklist();
    }

    private void checkRateLimit(String clientIp) {
        if (rateLimiter == null) {
            return;
        }
        long ipLimit = resolveIpLimit();
        if (ipLimit > 0 && clientIp != null && !clientIp.isBlank()
                && !rateLimiter.tryAcquire(RATE_LIMIT_IP_KEY + clientIp.trim(), RateLimitType.FIXED_WINDOW,
                        ipLimit, RATE_LIMIT_WINDOW)) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "验证码获取过于频繁，请稍后再试");
        }
        long globalLimit = properties.getGlobalLimitPerMinute();
        if (globalLimit > 0
                && !rateLimiter.tryAcquire(RATE_LIMIT_GLOBAL_KEY, RateLimitType.FIXED_WINDOW, globalLimit, RATE_LIMIT_WINDOW)) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "验证码获取请求过多，请稍后再试");
        }
    }

    private int resolveLength() {
        int length = properties.getLength();
        if (dynamicConfigService != null) {
            length = dynamicConfigService.getInt(CONFIG_LENGTH, length);
        }
        return Math.max(4, Math.min(6, length));
    }

    private Duration resolveTtl() {
        int seconds = properties.getTtlSeconds();
        if (dynamicConfigService != null) {
            seconds = dynamicConfigService.getInt(CONFIG_TTL_SECONDS, seconds);
        }
        return seconds > 0 ? Duration.ofSeconds(seconds) : Duration.ofSeconds(properties.getTtlSeconds());
    }

    private long resolveFailThreshold() {
        int threshold = properties.getFailThreshold();
        if (dynamicConfigService != null) {
            threshold = dynamicConfigService.getInt(CONFIG_FAIL_THRESHOLD, threshold);
        }
        return threshold;
    }

    private long resolveIpLimit() {
        int limit = properties.getIpLimitPerMinute();
        if (dynamicConfigService != null) {
            limit = dynamicConfigService.getInt(CONFIG_IP_LIMIT_PER_MINUTE, limit);
        }
        return limit;
    }
}
