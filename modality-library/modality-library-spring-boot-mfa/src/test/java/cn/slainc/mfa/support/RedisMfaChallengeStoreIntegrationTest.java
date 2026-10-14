package cn.slainc.mfa.support;

import cn.slainc.mfa.api.MfaChallengeStore;
import cn.slainc.mfa.support.RedisMfaChallengeStore;
import cn.slainc.mfa.challenge.MfaChallenge;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisMfaChallengeStoreIntegrationTest {

    private StringRedisTemplate redisTemplate;

    private RedisMfaChallengeStore store;

    @BeforeAll
    void setUpRedis() {
        RedisProbe.assumeRedis();
        redisTemplate = RedisProbe.newStringRedisTemplate();
        // 读穿透回落是设计行为（Redis 短暂不可用时写入降级到内存，恢复后仍可读）；
        // 集成测试用空内存回落 + 直接断言 Redis 里的键与 TTL 证明数据真的落在了 Redis
        store = new RedisMfaChallengeStore(redisTemplate, new cn.slainc.mfa.support.InMemoryMfaChallengeStore());
    }

    @AfterAll
    void cleanUp() {
        if (redisTemplate != null) {
            redisTemplate.delete(RedisMfaChallengeStore.KEY_PREFIX + "it-1");
            redisTemplate.delete(RedisMfaChallengeStore.KEY_PREFIX + "it-2");
            redisTemplate.delete(RedisMfaChallengeStore.KEY_PREFIX + "it-3");
        }
    }

    @Test
    void shouldRoundTripAChallengeWithAnExpiry() {
        MfaChallenge challenge = MfaChallenge.of("it-1", 7L, "alice", "TOTP", Duration.ofMinutes(5));

        store.save(challenge, Duration.ofMinutes(5));

        assertThat(redisTemplate.getExpire(RedisMfaChallengeStore.KEY_PREFIX + "it-1"))
                .isNotNull()
                .isPositive();
        assertThat(redisTemplate.opsForValue().get(RedisMfaChallengeStore.KEY_PREFIX + "it-1")).isNotBlank();
        assertThat(store.peek("it-1")).isEqualTo(challenge);
        assertThat(store.consume("it-1")).isEqualTo(challenge);
        assertThat(store.consume("it-1")).isNull();
    }

    @Test
    void shouldShareAChallengeBetweenStoreInstances() {
        RedisMfaChallengeStore another = new RedisMfaChallengeStore(redisTemplate);
        MfaChallenge challenge = MfaChallenge.of("it-2", 7L, "bob", "TOTP", Duration.ofMinutes(5));

        store.save(challenge, Duration.ofMinutes(5));

        assertThat(another.consume("it-2")).isEqualTo(challenge);
        assertThat(store.peek("it-2")).isNull();
    }

    @Test
    void shouldDropAChallengeAfterItsTtl() throws InterruptedException {
        MfaChallenge challenge = MfaChallenge.of("it-3", 7L, "carol", "TOTP", Duration.ofSeconds(1));

        store.save(challenge, Duration.ofSeconds(1));
        assertThat(store.peek("it-3")).isNotNull();

        Thread.sleep(1500L);

        assertThat(store.peek("it-3")).isNull();
    }
}
