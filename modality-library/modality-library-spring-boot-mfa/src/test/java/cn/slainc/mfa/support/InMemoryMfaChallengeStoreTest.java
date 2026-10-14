package cn.slainc.mfa.support;

import cn.slainc.mfa.challenge.MfaChallenge;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMfaChallengeStoreTest {

    private final InMemoryMfaChallengeStore store = new InMemoryMfaChallengeStore();

    @Test
    void shouldConsumeAChallengeOnlyOnce() {
        MfaChallenge challenge = MfaChallenge.of("c1", 1L, "alice", "TOTP", Duration.ofMinutes(5));
        store.save(challenge, Duration.ofMinutes(5));

        assertThat(store.peek("c1")).isEqualTo(challenge);
        assertThat(store.consume("c1")).isEqualTo(challenge);
        assertThat(store.consume("c1")).isNull();
        assertThat(store.peek("c1")).isNull();
    }

    @Test
    void shouldIgnoreExpiredChallenges() {
        store.save(new MfaChallenge("c2", 1L, "alice", "TOTP", System.currentTimeMillis() - 1000L),
                Duration.ofMinutes(5));

        assertThat(store.peek("c2")).isNull();
        assertThat(store.consume("c2")).isNull();
    }

    @Test
    void shouldHandleBlankAndUnknownIds() {
        assertThat(store.peek(null)).isNull();
        assertThat(store.consume(" ")).isNull();
        assertThat(store.peek("missing")).isNull();
        assertThat(store.consume("missing")).isNull();
        store.save(null, Duration.ofMinutes(5));
        store.save(new MfaChallenge("", 1L, "alice", "TOTP", 0L), Duration.ofMinutes(5));
        assertThat(store.size()).isZero();
    }

    @Test
    void shouldKeepTheStoreBounded() {
        InMemoryMfaChallengeStore bounded = new InMemoryMfaChallengeStore(16);
        for (int i = 0; i < 200; i++) {
            bounded.save(MfaChallenge.of("c" + i, 1L, "alice", "TOTP", Duration.ofMinutes(5)),
                    Duration.ofMinutes(5));
        }

        assertThat(bounded.size()).isLessThanOrEqualTo(16);
    }
}
