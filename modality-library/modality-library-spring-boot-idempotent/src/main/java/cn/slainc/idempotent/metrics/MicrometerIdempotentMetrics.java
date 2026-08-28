package cn.slainc.idempotent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MicrometerIdempotentMetrics extends LocalIdempotentMetrics {

    public static final String REQUESTS_METER = "modality.idempotent.requests";

    public static final String EXECUTED_METER = "modality.idempotent.executed";

    public static final String DUPLICATES_METER = "modality.idempotent.duplicates";

    public static final String CACHE_HITS_METER = "modality.idempotent.cache.hits";

    public static final String FAILURES_METER = "modality.idempotent.failures";

    public static final String RETRY_EXCEEDED_METER = "modality.idempotent.retry.exceeded";

    public static final String DUPLICATE_RATE_METER = "modality.idempotent.duplicate.rate";

    private final MeterRegistry registry;

    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

    public MicrometerIdempotentMetrics(MeterRegistry registry) {
        this.registry = registry;
        if (registry != null) {
            Gauge.builder(DUPLICATE_RATE_METER, this, metrics -> metrics.snapshot().duplicateRate())
                    .description("重复提交占比")
                    .register(registry);
        }
    }

    @Override
    public void recordRequest(String methodId) {
        super.recordRequest(methodId);
        increment(REQUESTS_METER, methodId);
    }

    @Override
    public void recordExecuted(String methodId) {
        super.recordExecuted(methodId);
        increment(EXECUTED_METER, methodId);
    }

    @Override
    public void recordDuplicate(String methodId) {
        super.recordDuplicate(methodId);
        increment(DUPLICATES_METER, methodId);
    }

    @Override
    public void recordCacheHit(String methodId) {
        super.recordCacheHit(methodId);
        increment(CACHE_HITS_METER, methodId);
    }

    @Override
    public void recordFailure(String methodId) {
        super.recordFailure(methodId);
        increment(FAILURES_METER, methodId);
    }

    @Override
    public void recordRetryExceeded(String methodId) {
        super.recordRetryExceeded(methodId);
        increment(RETRY_EXCEEDED_METER, methodId);
    }

    private void increment(String meter, String methodId) {
        if (registry == null) {
            return;
        }
        String normalized = normalize(methodId);
        counters.computeIfAbsent(meter + "|" + normalized,
                key -> Counter.builder(meter).tag("method", normalized).register(registry)).increment();
    }
}
