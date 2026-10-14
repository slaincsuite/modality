package cn.slainc.mfa.support;

import cn.slainc.mfa.challenge.MfaChallenge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisMfaChallengeStoreTest {

    private final Map<String, String> redis = new ConcurrentHashMap<>();

    private StringRedisTemplate redisTemplate;

    private ValueOperations<String, String> valueOperations;

    private InMemoryMfaChallengeStore fallback;

    private RedisMfaChallengeStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        when(valueOperations.getAndDelete(anyString()))
                .thenAnswer(invocation -> redis.remove(invocation.getArgument(0)));
        fallback = new InMemoryMfaChallengeStore();
        store = new RedisMfaChallengeStore(redisTemplate, fallback);
    }

    @Test
    void shouldStoreTheChallengeUnderTheDocumentedKey() {
        MfaChallenge challenge = MfaChallenge.of("c1", 7L, "alice", "TOTP", Duration.ofMinutes(5));

        store.save(challenge, Duration.ofMinutes(5));

        assertThat(redis).containsKey(RedisMfaChallengeStore.KEY_PREFIX + "c1");
        assertThat(store.peek("c1")).isEqualTo(challenge);
    }

    @Test
    void shouldConsumeTheChallengeOnlyOnce() {
        store.save(MfaChallenge.of("c1", 7L, "alice", "TOTP", Duration.ofMinutes(5)), Duration.ofMinutes(5));

        assertThat(store.consume("c1")).isNotNull();
        assertThat(store.consume("c1")).isNull();
        assertThat(store.peek("c1")).isNull();
    }

    @Test
    void shouldRoundTripAUsernameThatContainsTheFieldSeparator() {
        MfaChallenge challenge = MfaChallenge.of("c2", 7L, "a|b|c", "TOTP", Duration.ofMinutes(5));

        store.save(challenge, Duration.ofMinutes(5));

        assertThat(store.peek("c2")).isEqualTo(challenge);
    }

    @Test
    void shouldDegradeToMemoryWhenRedisFails() {
        doThrow(new IllegalStateException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        store.save(MfaChallenge.of("c3", 7L, "alice", "TOTP", Duration.ofMinutes(5)), Duration.ofMinutes(5));

        assertThat(redis).doesNotContainKey(RedisMfaChallengeStore.KEY_PREFIX + "c3");
        assertThat(store.peek("c3")).isNotNull();
        assertThat(store.consume("c3")).isNotNull();
        assertThat(store.consume("c3")).isNull();
    }

    @Test
    void shouldIgnoreMalformedOrExpiredPayloads() {
        redis.put(RedisMfaChallengeStore.KEY_PREFIX + "bad", "not-a-challenge");
        redis.put(RedisMfaChallengeStore.KEY_PREFIX + "old", "old|7|TOTP|1|alice");

        assertThat(store.peek("bad")).isNull();
        assertThat(store.peek("old")).isNull();
        assertThat(store.consume("bad")).isNull();
    }

    @Test
    void shouldIgnoreBlankIds() {
        assertThat(store.peek(null)).isNull();
        assertThat(store.consume("  ")).isNull();
        store.save(null, Duration.ofMinutes(5));
        assertThat(redis).isEmpty();
    }
}
