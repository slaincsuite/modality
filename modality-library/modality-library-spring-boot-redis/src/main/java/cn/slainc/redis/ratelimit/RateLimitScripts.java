package cn.slainc.redis.ratelimit;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

public final class RateLimitScripts {

    public static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local permits = tonumber(ARGV[4])
            local ttl = tonumber(ARGV[5])
            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then
              tokens = capacity
              ts = now
            end
            local delta = math.max(0, now - ts) / 1000.0
            tokens = math.min(capacity, tokens + delta * rate)
            local allowed = 0
            if tokens >= permits then
              tokens = tokens - permits
              allowed = 1
            end
            redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, ttl)
            return allowed
            """;

    public static final String SLIDING_WINDOW_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count < limit then
              redis.call('ZADD', key, now, member)
              redis.call('PEXPIRE', key, window)
              return 1
            end
            redis.call('PEXPIRE', key, window)
            return 0
            """;

    public static final String LEAKY_BUCKET_LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local leakRate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local data = redis.call('HMGET', key, 'water', 'ts')
            local water = tonumber(data[1])
            local ts = tonumber(data[2])
            if water == nil then
              water = 0
              ts = now
            end
            local delta = math.max(0, now - ts) / 1000.0
            water = math.max(0, water - delta * leakRate)
            local allowed = 0
            if water + 1 <= capacity then
              water = water + 1
              allowed = 1
            end
            redis.call('HMSET', key, 'water', water, 'ts', now)
            redis.call('PEXPIRE', key, ttl)
            return allowed
            """;

    public static final String FIXED_WINDOW_LUA = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local count = redis.call('INCR', key)
            if count == 1 then
              redis.call('PEXPIRE', key, window)
            end
            if count > limit then
              return 0
            end
            return 1
            """;

    public static final RedisScript<Long> TOKEN_BUCKET = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);

    public static final RedisScript<Long> SLIDING_WINDOW = new DefaultRedisScript<>(SLIDING_WINDOW_LUA, Long.class);

    public static final RedisScript<Long> LEAKY_BUCKET = new DefaultRedisScript<>(LEAKY_BUCKET_LUA, Long.class);

    public static final RedisScript<Long> FIXED_WINDOW = new DefaultRedisScript<>(FIXED_WINDOW_LUA, Long.class);

    private RateLimitScripts() {
    }

    public static RedisScript<Long> of(RateLimitType type) {
        return switch (type) {
            case SLIDING_WINDOW -> SLIDING_WINDOW;
            case LEAKY_BUCKET -> LEAKY_BUCKET;
            case FIXED_WINDOW -> FIXED_WINDOW;
            default -> TOKEN_BUCKET;
        };
    }
}
