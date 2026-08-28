package cn.slainc.idempotent.store;

import cn.slainc.idempotent.support.IdempotentKeys;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class RedisIdempotentStore implements IdempotentStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private static final String CONSUME_TOKEN_SCRIPT = """
            local value = redis.call('GET', KEYS[1])
            if value then
                redis.call('DEL', KEYS[1])
            end
            return value
            """;

    private static final String INCREASE_FAILURE_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<String> consumeTokenScript =
            new DefaultRedisScript<>(CONSUME_TOKEN_SCRIPT, String.class);

    private final DefaultRedisScript<Long> increaseFailureScript =
            new DefaultRedisScript<>(INCREASE_FAILURE_SCRIPT, Long.class);

    public RedisIdempotentStore(StringRedisTemplate redisTemplate) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("StringRedisTemplate 不能为空");
        }
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(key, JsonUtils.toJson(IdempotentRecord.pending()), actualTtl(ttl)));
    }

    @Override
    public void release(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public boolean isHeld(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public Optional<IdempotentRecord> findRecord(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        IdempotentRecord record = parse(value);
        return record == null || record.isPending() ? Optional.empty() : Optional.of(record);
    }

    @Override
    public void saveRecord(String key, IdempotentRecord record, Duration ttl) {
        redisTemplate.opsForValue().set(key, JsonUtils.toJson(record), actualTtl(ttl));
    }

    @Override
    public long increaseFailure(String key, Duration window) {
        Long count = redisTemplate.execute(increaseFailureScript,
                List.of(IdempotentKeys.failureKey(key)), String.valueOf(actualTtl(window).toMillis()));
        return count == null ? 0L : count;
    }

    @Override
    public long failureCount(String key) {
        String value = redisTemplate.opsForValue().get(IdempotentKeys.failureKey(key));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    @Override
    public void clearFailure(String key) {
        redisTemplate.delete(IdempotentKeys.failureKey(key));
    }

    @Override
    public void saveToken(String token, String owner, Duration ttl) {
        redisTemplate.opsForValue().set(IdempotentKeys.tokenKey(token),
                JsonUtils.toJson(IdempotentRecord.token(owner)), actualTtl(ttl));
    }

    @Override
    public String consumeToken(String token) {
        String value = redisTemplate.execute(consumeTokenScript, List.of(IdempotentKeys.tokenKey(token)));
        if (value == null) {
            return null;
        }
        IdempotentRecord record = parse(value);
        return record == null ? "" : record.payload();
    }

    @Override
    public void clear(String key) {
        redisTemplate.delete(key);
    }

    private static IdempotentRecord parse(String value) {
        try {
            return JsonUtils.parse(value, IdempotentRecord.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Duration actualTtl(Duration ttl) {
        return ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_TTL : ttl;
    }
}
