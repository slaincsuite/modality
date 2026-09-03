package cn.slainc.logger.monitor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class InMemoryLogMetrics implements LogMetrics {

    private final Map<String, LongAdder> levelCounters = new ConcurrentHashMap<>();

    private final LongAdder total = new LongAdder();

    private final LongAdder withException = new LongAdder();

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
        updatedAt = Instant.now();
    }

    @Override
    public void reset() {
        levelCounters.clear();
        total.reset();
        withException.reset();
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
}
