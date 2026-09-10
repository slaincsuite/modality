package cn.slainc.jwt.revoke;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

public class RedisRevocationStore implements RevocationStore {

    public static final String KEY_PREFIX = "modality:jwt:revoked:";

    private final StringRedisTemplate redisTemplate;

    public RedisRevocationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("吊销记录必须携带 jti");
        }
        Instant effectiveExpiry = expiresAt == null ? Instant.now().plusSeconds(86400) : expiresAt;
        long remainingSeconds = Duration.between(Instant.now(), effectiveExpiry).toSeconds();
        long ttl = Math.max(1, remainingSeconds);
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttl));
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(KEY_PREFIX + jti);
        return Boolean.TRUE.equals(exists);
    }
}
