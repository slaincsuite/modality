package cn.slainc.idempotent.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentMetricsTest {

    @Test
    void localMetricsTrackDuplicateRateAndMethodDistribution() {
        LocalIdempotentMetrics metrics = new LocalIdempotentMetrics();
        metrics.recordRequest("A#m");
        metrics.recordRequest("A#m");
        metrics.recordRequest("A#m");
        metrics.recordDuplicate("A#m");
        metrics.recordExecuted("A#m");
        metrics.recordCacheHit("A#m");
        metrics.recordFailure("A#m");
        metrics.recordRetryExceeded("A#m");
        IdempotentMetricsSnapshot snapshot = metrics.snapshot();
        assertThat(snapshot.requests()).isEqualTo(3L);
        assertThat(snapshot.duplicates()).isEqualTo(1L);
        assertThat(snapshot.duplicateRate()).isCloseTo(1.0D / 3.0D, org.assertj.core.data.Offset.offset(1.0E-9D));
        assertThat(snapshot.duplicatesByMethod()).containsEntry("A#m", 1L);
        assertThat(snapshot.executed()).isEqualTo(1L);
        assertThat(snapshot.cacheHits()).isEqualTo(1L);
        assertThat(snapshot.failures()).isEqualTo(1L);
        assertThat(snapshot.retryExceeded()).isEqualTo(1L);
    }

    @Test
    void micrometerMetricsRegisteredAndReadable() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerIdempotentMetrics metrics = new MicrometerIdempotentMetrics(registry);
        metrics.recordRequest("B#m");
        metrics.recordRequest("B#m");
        metrics.recordDuplicate("B#m");
        assertThat(registry.get(MicrometerIdempotentMetrics.REQUESTS_METER).tag("method", "B#m").counter().count())
                .isEqualTo(2.0D);
        assertThat(registry.get(MicrometerIdempotentMetrics.DUPLICATES_METER).tag("method", "B#m").counter().count())
                .isEqualTo(1.0D);
        assertThat(registry.get(MicrometerIdempotentMetrics.DUPLICATE_RATE_METER).gauge().value())
                .isCloseTo(0.5D, org.assertj.core.data.Offset.offset(1.0E-9D));
        assertThat(metrics.snapshot().duplicatesByMethod()).containsEntry("B#m", 1L);
    }

    @Test
    void metricsStillWorkWithoutRegistry() {
        MicrometerIdempotentMetrics metrics = new MicrometerIdempotentMetrics(null);
        metrics.recordRequest("C#m");
        metrics.recordDuplicate("C#m");
        assertThat(metrics.snapshot().duplicateRate()).isEqualTo(1.0D);
    }
}
