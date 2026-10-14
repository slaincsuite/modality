package cn.slainc.mfa.api.impl;

import cn.slainc.mfa.api.MfaChallengeStore;
import cn.slainc.mfa.challenge.MfaChallenge;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisMfaChallengeStore implements MfaChallengeStore {

    public static final String KEY_PREFIX = "modality:mfa:challenge:";

    private static final Logger log = LoggerFactory.getLogger(RedisMfaChallengeStore.class);

    private static final char FIELD_SEPARATOR = '|';

    private static final int FIELD_COUNT = 5;

    private static final int MAX_FIELD_LENGTH = 65536;

    private final StringRedisTemplate redisTemplate;

    private final MfaChallengeStore fallback;

    public RedisMfaChallengeStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, new InMemoryMfaChallengeStore());
    }

    public RedisMfaChallengeStore(StringRedisTemplate redisTemplate, MfaChallengeStore fallback) {
        this.redisTemplate = redisTemplate;
        this.fallback = fallback == null ? new InMemoryMfaChallengeStore() : fallback;
    }

    @Override
    public void save(MfaChallenge challenge, Duration ttl) {
        if (challenge == null || StringUtils.isBlank(challenge.id())) {
            return;
        }
        Duration effective = ttl == null || ttl.isZero() || ttl.isNegative() ? MfaChallenge.DEFAULT_TTL : ttl;
        try {
            redisTemplate.opsForValue().set(redisKey(challenge.id()), serialize(challenge), effective);
        } catch (RuntimeException e) {
            log.warn("二次验证挑战写入 Redis 失败，已回落到内存存储，原因：{}", e.getMessage());
            fallback.save(challenge, effective);
        }
    }

    @Override
    public MfaChallenge consume(String challengeId) {
        if (StringUtils.isBlank(challengeId)) {
            return null;
        }
        String key = redisKey(challengeId);
        try {
            MfaChallenge challenge = deserialize(redisTemplate.opsForValue().getAndDelete(key), key);
            if (challenge != null) {
                return challenge.expired() ? null : challenge;
            }
        } catch (RuntimeException e) {
            log.warn("二次验证挑战从 Redis 取出失败，已回落到内存存储，原因：{}", e.getMessage());
        }
        return fallback.consume(challengeId);
    }

    @Override
    public MfaChallenge peek(String challengeId) {
        if (StringUtils.isBlank(challengeId)) {
            return null;
        }
        String key = redisKey(challengeId);
        try {
            MfaChallenge challenge = deserialize(redisTemplate.opsForValue().get(key), key);
            if (challenge != null) {
                return challenge.expired() ? null : challenge;
            }
        } catch (RuntimeException e) {
            log.warn("二次验证挑战从 Redis 读取失败，已回落到内存存储，原因：{}", e.getMessage());
        }
        return fallback.peek(challengeId);
    }

    private String redisKey(String challengeId) {
        return KEY_PREFIX + challengeId;
    }

    private String serialize(MfaChallenge challenge) {
        return String.join(String.valueOf(FIELD_SEPARATOR),
                StringUtils.nullToEmpty(challenge.id()),
                challenge.userId() == null ? "" : challenge.userId().toString(),
                StringUtils.nullToEmpty(challenge.method()),
                Long.toString(challenge.expiresAt()),
                StringUtils.nullToEmpty(challenge.username()));
    }

    private MfaChallenge deserialize(String payload, String key) {
        if (payload == null) {
            return null;
        }
        if (payload.length() > MAX_FIELD_LENGTH) {
            log.warn("忽略超长的二次验证挑战载荷 key={}", key);
            return null;
        }
        String[] parts = payload.split("\\|", FIELD_COUNT);
        if (parts.length < FIELD_COUNT) {
            log.warn("忽略格式非法的二次验证挑战载荷 key={}", key);
            return null;
        }
        try {
            Long userId = parts[1].isEmpty() ? null : Long.valueOf(parts[1]);
            return new MfaChallenge(parts[0], userId, parts[4].isEmpty() ? null : parts[4],
                    parts[2].isEmpty() ? null : parts[2], Long.parseLong(parts[3]));
        } catch (NumberFormatException e) {
            log.warn("忽略格式非法的二次验证挑战载荷 key={}", key);
            return null;
        }
    }
}
