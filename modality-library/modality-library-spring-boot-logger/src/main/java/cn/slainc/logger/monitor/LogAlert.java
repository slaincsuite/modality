package cn.slainc.logger.monitor;

import java.time.Instant;

public record LogAlert(String type, String message, double errorRate, long errorCount, long total,
                       long windowSeconds, Instant timestamp, String sample) {
}
