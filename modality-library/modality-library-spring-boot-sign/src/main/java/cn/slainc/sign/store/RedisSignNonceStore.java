package cn.slainc.sign.store;

import cn.slainc.sign.config.SignKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

public class RedisSignNonceStore implements SignNonceStore {

    private static final long DEFAULT_TTL_SECONDS = 300;

    private static final String INCREASE_FAILURE_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<Long> increaseFailureScript =
            new DefaultRedisScript<>(INCREASE_FAILURE_SCRIPT, Long.class);

    public RedisSignNonceStore(StringRedisTemplate redisTemplate) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("StringRedisTemplate 不能为空");
        }
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String appId, String nonce, long ttlSeconds) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                SignKeys.nonceKey(appId, nonce), "1", Duration.ofSeconds(actualTtl(ttlSeconds)));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public long incrementFailure(String appId, long ttlSeconds) {
        Long count = redisTemplate.execute(increaseFailureScript,
                List.of(SignKeys.failKey(appId)), String.valueOf(actualTtl(ttlSeconds) * 1000L));
        return count == null ? 0L : count;
    }

    @Override
    public long failureCount(String appId) {
        String value = redisTemplate.opsForValue().get(SignKeys.failKey(appId));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private long actualTtl(long ttlSeconds) {
        return ttlSeconds <= 0 ? DEFAULT_TTL_SECONDS : ttlSeconds;
    }
}
