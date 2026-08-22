package cn.slainc.mybatis.sql;

import java.time.LocalDateTime;

public record SqlAuditEvent(String statementId, String commandType, String sql, String parameters,
                            long elapsedMillis, int affectedRows, boolean success, String errorMessage,
                            String traceId, LocalDateTime happenedAt) {
}
