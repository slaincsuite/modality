package cn.slainc.idempotent.metrics;

import java.util.Map;

public record IdempotentMetricsSnapshot(
        long requests,
        long executed,
        long duplicates,
        long cacheHits,
        long failures,
        long retryExceeded,
        double duplicateRate,
        Map<String, Long> duplicatesByMethod) {

    public IdempotentMetricsSnapshot {
        duplicatesByMethod = duplicatesByMethod == null ? Map.of() : Map.copyOf(duplicatesByMethod);
    }
}
