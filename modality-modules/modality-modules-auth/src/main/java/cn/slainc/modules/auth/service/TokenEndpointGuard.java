package cn.slainc.modules.auth.service;

import cn.slainc.core.context.RequestContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.redis.ratelimit.RateLimitType;
import cn.slainc.redis.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 匿名可达的令牌端点必须先限流再鉴权：优先复用 redis 限流设施，缺失或故障时退回本节点内存窗口
public class TokenEndpointGuard {

    private static final Logger log = LoggerFactory.getLogger(TokenEndpointGuard.class);

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final int MAX_LOCAL_ENTRIES = 10000;

    private final AuthProperties properties;

    private final ObjectProvider<RateLimiter> rateLimiter;

    private final Map<String, long[]> localWindows = new ConcurrentHashMap<>();

    public TokenEndpointGuard(AuthProperties properties, ObjectProvider<RateLimiter> rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    public void checkRevoke() {
        check("revoke", properties.getToken().getRevokeLimitPerMinute());
    }

    public void checkIntrospect() {
        check("introspect", properties.getToken().getIntrospectLimitPerMinute());
    }

    private void check(String action, long limitPerMinute) {
        if (limitPerMinute <= 0) {
            return;
        }
        String clientIp = RequestContext.clientIp();
        String key = "auth:token:" + action + ":"
                + (clientIp == null || clientIp.isBlank() ? "未知IP" : clientIp);
        RateLimiter limiter = rateLimiter == null ? null : rateLimiter.getIfAvailable();
        if (limiter != null) {
            try {
                if (!limiter.tryAcquire(key, RateLimitType.FIXED_WINDOW, limitPerMinute, WINDOW)) {
                    throw tooMany();
                }
                return;
            } catch (BizException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.warn("令牌接口限流设施不可用，降级为单机内存限流：{}", exception.getMessage());
            }
        }
        if (!allowLocally(key, limitPerMinute)) {
            throw tooMany();
        }
    }

    private boolean allowLocally(String key, long limit) {
        long now = System.currentTimeMillis();
        if (localWindows.size() > MAX_LOCAL_ENTRIES) {
            localWindows.entrySet().removeIf(entry -> now - entry.getValue()[0] >= WINDOW.toMillis());
        }
        long[] window = localWindows.computeIfAbsent(key, ignored -> new long[]{now, 0L});
        synchronized (window) {
            if (now - window[0] >= WINDOW.toMillis()) {
                window[0] = now;
                window[1] = 0L;
            }
            window[1]++;
            return window[1] <= limit;
        }
    }

    private BizException tooMany() {
        return new BizException(ResultCode.TOO_MANY_REQUESTS, "令牌接口请求过于频繁，请稍后再试");
    }
}
