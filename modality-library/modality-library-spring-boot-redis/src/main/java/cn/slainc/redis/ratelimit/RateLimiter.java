package cn.slainc.redis.ratelimit;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisProperties properties;

    private final RateLimiterStats stats = new RateLimiterStats();

    public RateLimiter(StringRedisTemplate stringRedisTemplate, RedisProperties properties) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public RateLimiterStats getStats() {
        return stats;
    }

    public boolean tryAcquire(String key, RateLimitType type, long limit, Duration window) {
        return tryAcquire(key, type, limit, window, 1L);
    }

    public boolean tryAcquire(String key, RateLimitType type, long limit, Duration window, long permits) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("限流键不能为空");
        }
        long effectiveLimit = limit > 0L ? limit : properties.getRateLimit().getDefaultLimit();
        Duration effectiveWindow = window == null || window.isZero() || window.isNegative()
                ? properties.getRateLimit().getDefaultWindow()
                : window;
        String redisKey = CacheKeys.RATE_LIMIT + (type == null ? "token_bucket" : type.name().toLowerCase(Locale.ROOT))
                + ":" + key;
        try {
            Long result = stringRedisTemplate.execute(RateLimitScripts.of(type),
                    Collections.singletonList(redisKey),
                    buildArgs(type, effectiveLimit, effectiveWindow, Math.max(permits, 1L)));
            boolean allowed = result != null && result == 1L;
            if (allowed) {
                stats.recordAllowed();
            } else {
                stats.recordRejected();
            }
            return allowed;
        } catch (Exception exception) {
            stats.recordFailed();
            log.warn("限流脚本执行失败，键={}，原因={}", redisKey, exception.getMessage());
            throw exception;
        }
    }

    public void reset(String key, RateLimitType type) {
        if (key == null) {
            return;
        }
        stringRedisTemplate.delete(CacheKeys.RATE_LIMIT
                + (type == null ? "token_bucket" : type.name().toLowerCase(Locale.ROOT)) + ":" + key);
    }

    private Object[] buildArgs(RateLimitType type, long limit, Duration window, long permits) {
        long windowMillis = window.toMillis();
        double perSecond = windowMillis <= 0L ? limit : (double) limit * 1000d / windowMillis;
        long now = System.currentTimeMillis();
        return switch (type == null ? RateLimitType.TOKEN_BUCKET : type) {
            case SLIDING_WINDOW -> new Object[]{String.valueOf(now), String.valueOf(windowMillis),
                    String.valueOf(limit), now + "-" + UUID.randomUUID().toString().substring(0, 8),
                    String.valueOf(permits)};
            case LEAKY_BUCKET -> new Object[]{String.valueOf(limit), String.valueOf(perSecond),
                    String.valueOf(now), String.valueOf(windowMillis * 2), String.valueOf(permits)};
            case FIXED_WINDOW -> new Object[]{String.valueOf(limit), String.valueOf(windowMillis),
                    String.valueOf(permits)};
            default -> new Object[]{String.valueOf(perSecond), String.valueOf(limit), String.valueOf(now),
                    String.valueOf(permits), String.valueOf(windowMillis * 2)};
        };
    }
}
