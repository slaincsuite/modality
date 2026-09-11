package cn.slainc.captcha.store;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisCaptchaStore implements CaptchaStore {

    private static final String LOCK_VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    public RedisCaptchaStore(StringRedisTemplate redisTemplate) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void remove(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public long failCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : parse(value);
    }

    @Override
    public long incrementFail(String key, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return count == null ? 0L : count;
    }

    @Override
    public boolean isLocked(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void lock(String key, Duration ttl) {
        redisTemplate.opsForValue().setIfAbsent(key, LOCK_VALUE, ttl);
    }

    private long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
