package cn.slainc.logger.monitor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class InMemoryLogMetrics implements LogMetrics {

    private static final long BUCKET_MILLIS = 1000L;

    private static final long RETENTION_MILLIS = 10 * 60 * 1000L;

    private static final long PRUNE_INTERVAL_MASK = 0x3FFL;

    private final Map<String, LongAdder> levelCounters = new ConcurrentHashMap<>();

    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();

    private final LongAdder total = new LongAdder();

    private final LongAdder withException = new LongAdder();

    private final AtomicLong recorded = new AtomicLong();

    private final Instant startedAt = Instant.now();

    private volatile Instant updatedAt = Instant.now();

    @Override
    public void record(String level, String loggerName, boolean hasException) {
        String normalized = level == null ? "UNKNOWN" : level.toUpperCase(java.util.Locale.ROOT);
        levelCounters.computeIfAbsent(normalized, key -> new LongAdder()).increment();
        total.increment();
        if (hasException) {
            withException.increment();
        }
        long now = System.currentTimeMillis();
        buckets.computeIfAbsent(now / BUCKET_MILLIS, key -> new Bucket()).add(normalized, hasException);
        if ((recorded.incrementAndGet() & PRUNE_INTERVAL_MASK) == 0L) {
            prune(now);
        }
        updatedAt = Instant.now();
    }

    @Override
    public void reset() {
        levelCounters.clear();
        buckets.clear();
        total.reset();
        withException.reset();
        recorded.set(0L);
        updatedAt = Instant.now();
    }

    @Override
    public LogMetricsSnapshot snapshot() {
        Map<String, Long> levels = new LinkedHashMap<>();
        long errorCount = 0L;
        long warnCount = 0L;
        for (Map.Entry<String, LongAdder> entry : levelCounters.entrySet()) {
            long value = entry.getValue().sum();
            levels.put(entry.getKey(), value);
            if ("ERROR".equals(entry.getKey())) {
                errorCount = value;
            } else if ("WARN".equals(entry.getKey())) {
                warnCount = value;
            }
        }
        return new LogMetricsSnapshot(total.sum(), errorCount, warnCount, withException.sum(),
                Map.copyOf(levels), startedAt, updatedAt);
    }

    @Override
    public LogMetricsSnapshot windowSnapshot(long windowMillis) {
        long window = Math.max(windowMillis, BUCKET_MILLIS);
        long now = System.currentTimeMillis();
        long from = now - window;
        long windowTotal = 0L;
        long windowException = 0L;
        Map<String, Long> levels = new LinkedHashMap<>();
        for (Map.Entry<Long, Bucket> entry : buckets.entrySet()) {
            if (entry.getKey() * BUCKET_MILLIS + BUCKET_MILLIS <= from) {
                continue;
            }
            Bucket bucket = entry.getValue();
            windowTotal += bucket.total();
            windowException += bucket.withException();
            bucket.levels().forEach((level, adder) -> levels.merge(level, adder.sum(), Long::sum));
        }
        long errorCount = levels.getOrDefault("ERROR", 0L);
        long warnCount = levels.getOrDefault("WARN", 0L);
        return new LogMetricsSnapshot(windowTotal, errorCount, warnCount, windowException,
                Map.copyOf(levels), Instant.ofEpochMilli(from), Instant.ofEpochMilli(now));
    }

    private void prune(long now) {
        long deadline = now - RETENTION_MILLIS;
        buckets.keySet().removeIf(bucketId -> bucketId * BUCKET_MILLIS + BUCKET_MILLIS <= deadline);
    }

    private static final class Bucket {

        private final LongAdder total = new LongAdder();

        private final LongAdder withException = new LongAdder();

        private final Map<String, LongAdder> levels = new ConcurrentHashMap<>();

        void add(String level, boolean hasException) {
            levels.computeIfAbsent(level, key -> new LongAdder()).increment();
            total.increment();
            if (hasException) {
                this.withException.increment();
            }
        }

        long total() {
            return total.sum();
        }

        long withException() {
            return withException.sum();
        }

        Map<String, LongAdder> levels() {
            return levels;
        }
    }
}
