package cn.slainc.job.support;

import io.github.resilience4j.core.IntervalFunction;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryBackoffTest {

    @Test
    void shouldGrowExponentiallyAndStopAtMaxInterval() {
        IntervalFunction backoff = RetryBackoff.of(Duration.ofMillis(100), 2.0d, Duration.ofMillis(1000));

        assertThat(backoff.apply(1)).isEqualTo(100L);
        assertThat(backoff.apply(2)).isEqualTo(200L);
        assertThat(backoff.apply(3)).isEqualTo(400L);
        assertThat(backoff.apply(4)).isEqualTo(800L);
        assertThat(backoff.apply(5)).isEqualTo(1000L);
        assertThat(backoff.apply(9)).isEqualTo(1000L);
    }

    @Test
    void shouldUseSafeDefaultsForBlankConfiguration() {
        IntervalFunction backoff = RetryBackoff.of(Duration.ZERO, 0.5d, null);

        assertThat(backoff.apply(1)).isEqualTo(1L);
        assertThat(backoff.apply(2)).isEqualTo(1L);
        assertThat(backoff.apply(50)).isLessThanOrEqualTo(RetryBackoff.DEFAULT_MAX_INTERVAL.toMillis());
    }
}
