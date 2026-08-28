package cn.slainc.idempotent.metrics;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class LocalIdempotentMetrics implements IdempotentMetrics {

    private final AtomicLong requests = new AtomicLong();

    private final AtomicLong executed = new AtomicLong();

    private final AtomicLong duplicates = new AtomicLong();

    private final AtomicLong cacheHits = new AtomicLong();

    private final AtomicLong failures = new AtomicLong();

    private final AtomicLong retryExceeded = new AtomicLong();

    private final ConcurrentMap<String, AtomicLong> duplicatesByMethod = new ConcurrentHashMap<>();

    @Override
    public void recordRequest(String methodId) {
        requests.incrementAndGet();
    }

    @Override
    public void recordExecuted(String methodId) {
        executed.incrementAndGet();
    }

    @Override
    public void recordDuplicate(String methodId) {
        duplicates.incrementAndGet();
        duplicatesByMethod.computeIfAbsent(normalize(methodId), key -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public void recordCacheHit(String methodId) {
        cacheHits.incrementAndGet();
    }

    @Override
    public void recordFailure(String methodId) {
        failures.incrementAndGet();
    }

    @Override
    public void recordRetryExceeded(String methodId) {
        retryExceeded.incrementAndGet();
    }

    @Override
    public IdempotentMetricsSnapshot snapshot() {
        long total = requests.get();
        long duplicate = duplicates.get();
        Map<String, Long> byMethod = new TreeMap<>();
        duplicatesByMethod.forEach((key, value) -> byMethod.put(key, value.get()));
        return new IdempotentMetricsSnapshot(total, executed.get(), duplicate, cacheHits.get(), failures.get(),
                retryExceeded.get(), total == 0L ? 0.0D : (double) duplicate / (double) total, byMethod);
    }

    public void reset() {
        requests.set(0L);
        executed.set(0L);
        duplicates.set(0L);
        cacheHits.set(0L);
        failures.set(0L);
        retryExceeded.set(0L);
        duplicatesByMethod.clear();
    }

    protected static String normalize(String methodId) {
        return methodId == null || methodId.isBlank() ? "未知方法" : methodId;
    }
}
