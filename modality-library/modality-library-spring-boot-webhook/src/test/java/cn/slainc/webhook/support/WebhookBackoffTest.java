package cn.slainc.webhook.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookBackoffTest {

    private static final Duration INITIAL = Duration.ofSeconds(30);

    private static final Duration MAX = Duration.ofMinutes(30);

    @Test
    void shouldWaitTheInitialBackoffAfterTheFirstFailure() {
        assertThat(WebhookBackoff.nextDelay(1, INITIAL, MAX)).isEqualTo(INITIAL);
        assertThat(WebhookBackoff.nextDelay(0, INITIAL, MAX)).isEqualTo(INITIAL);
    }

    @Test
    void shouldDoubleTheDelayOnEveryFurtherFailure() {
        assertThat(WebhookBackoff.nextDelay(2, INITIAL, MAX)).isEqualTo(Duration.ofSeconds(60));
        assertThat(WebhookBackoff.nextDelay(3, INITIAL, MAX)).isEqualTo(Duration.ofMinutes(2));
        assertThat(WebhookBackoff.nextDelay(4, INITIAL, MAX)).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void shouldCapTheDelayAtTheMaximum() {
        assertThat(WebhookBackoff.nextDelay(20, INITIAL, MAX)).isEqualTo(MAX);
        assertThat(WebhookBackoff.nextDelay(Integer.MAX_VALUE, INITIAL, MAX)).isEqualTo(MAX);
    }

    @Test
    void shouldTolerateMissingDurations() {
        assertThat(WebhookBackoff.nextDelay(3, null, null)).isEqualTo(Duration.ZERO);
        assertThat(WebhookBackoff.nextDelay(3, INITIAL, null)).isEqualTo(INITIAL);
    }
}
