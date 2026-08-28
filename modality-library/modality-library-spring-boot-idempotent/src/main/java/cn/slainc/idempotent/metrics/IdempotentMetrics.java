package cn.slainc.idempotent.metrics;

public interface IdempotentMetrics {

    void recordRequest(String methodId);

    void recordExecuted(String methodId);

    void recordDuplicate(String methodId);

    void recordCacheHit(String methodId);

    void recordFailure(String methodId);

    void recordRetryExceeded(String methodId);

    IdempotentMetricsSnapshot snapshot();
}
