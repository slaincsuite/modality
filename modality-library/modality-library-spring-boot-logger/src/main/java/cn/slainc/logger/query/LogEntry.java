package cn.slainc.logger.query;

import java.time.Instant;
import java.util.Map;

public record LogEntry(Instant timestamp, String timestampText, String level, String logger, String thread,
                       String message, String traceId, String spanId, String userId, String tenantId,
                       String exceptionClass, String exceptionMessage, String source, Map<String, Object> fields) {
}
