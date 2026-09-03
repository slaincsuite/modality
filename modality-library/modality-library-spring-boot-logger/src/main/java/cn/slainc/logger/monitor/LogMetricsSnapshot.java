package cn.slainc.logger.monitor;

import java.time.Instant;
import java.util.Map;

public record LogMetricsSnapshot(long total, long errorCount, long warnCount, long withExceptionCount,
                                 Map<String, Long> levelCounts, Instant startedAt, Instant updatedAt) {

    public double errorRate() {
        return total <= 0L ? 0.0d : (double) errorCount / (double) total;
    }
}
