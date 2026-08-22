package cn.slainc.mybatis.sql;

import java.time.LocalDateTime;

public record SlowSqlRecord(String statementId, String sql, long elapsedMillis, LocalDateTime happenedAt) {
}
