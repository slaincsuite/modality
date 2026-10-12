package cn.slainc.apikey.guard;

import cn.dev33.satoken.apikey.exception.ApiKeyException;
import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.slainc.apikey.config.ApiKeyProperties;
import cn.slainc.redis.ratelimit.RateLimitType;
import cn.slainc.redis.ratelimit.RateLimiter;
import cn.slainc.utils.net.IpMatcher;
import cn.slainc.utils.net.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;

public class ApiKeyGuard {

    public static final String EXTRA_IP_WHITELIST = "ipWhitelist";

    public static final String EXTRA_QPS_LIMIT = "qpsLimit";

    private static final Duration QPS_WINDOW = Duration.ofSeconds(1);

    private final ObjectProvider<RateLimiter> rateLimiter;

    private final ApiKeyStatsRecorder statsRecorder;

    private final ApiKeyProperties properties;

    public ApiKeyGuard(ObjectProvider<RateLimiter> rateLimiter, ApiKeyStatsRecorder statsRecorder,
                       ApiKeyProperties properties) {
        this.rateLimiter = rateLimiter;
        this.statsRecorder = statsRecorder;
        this.properties = properties;
    }

    public void check(String apiKey, ApiKeyModel model) {
        if (apiKey == null || model == null) {
            return;
        }
        checkIpWhitelist(apiKey, model);
        checkRateLimit(apiKey, model);
        if (statsRecorder != null) {
            statsRecorder.record(apiKey);
        }
    }

    private void checkIpWhitelist(String apiKey, ApiKeyModel model) {
        List<String> whitelist = readWhitelist(model);
        if (whitelist.isEmpty()) {
            return;
        }
        HttpServletRequest request = currentRequest();
        String requestIp = request == null ? null
                : IpUtils.resolveClientIp(request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP"),
                        request.getHeader("CF-Connecting-IP"), request.getRemoteAddr());
        if (requestIp == null || requestIp.isBlank()) {
            throw new ApiKeyException("API Key " + mask(apiKey) + " 的请求无法解析来源 IP，拒绝访问");
        }
        for (String entry : whitelist) {
            if (matchesIp(requestIp, entry)) {
                return;
            }
        }
        throw new ApiKeyException("API Key " + mask(apiKey) + " 的来源 IP 不在白名单：" + requestIp);
    }

    private void checkRateLimit(String apiKey, ApiKeyModel model) {
        long qps = readQpsLimit(model);
        if (qps <= 0) {
            qps = properties.getGuard().getDefaultQpsLimit();
        }
        if (qps <= 0) {
            return;
        }
        RateLimiter limiter = rateLimiter.getIfAvailable();
        if (limiter == null) {
            return;
        }
        boolean acquired = limiter.tryAcquire("apikey:" + ApiKeyStatsKeys.fingerprint(apiKey),
                RateLimitType.TOKEN_BUCKET, qps, QPS_WINDOW);
        if (!acquired) {
            throw new ApiKeyException("API Key " + mask(apiKey) + " 请求频率超过限制，请稍后重试");
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readWhitelist(ApiKeyModel model) {
        Object value = model.getExtra(EXTRA_IP_WHITELIST);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> item == null ? "" : String.valueOf(item).trim())
                    .filter(item -> !item.isEmpty()).toList();
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return List.of();
        }
        return (List<String>) List.of(text.split(","));
    }

    private long readQpsLimit(ApiKeyModel model) {
        Object value = model.getExtra(EXTRA_QPS_LIMIT);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private boolean matchesIp(String requestIp, String entry) {
        return IpMatcher.matches(requestIp, entry);
    }

    private static String mask(String apiKey) {
        String fingerprint = ApiKeyStatsKeys.fingerprint(apiKey);
        return fingerprint + "****";
    }
}
