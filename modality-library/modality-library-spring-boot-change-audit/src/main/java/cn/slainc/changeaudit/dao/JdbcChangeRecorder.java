package cn.slainc.changeaudit.dao;

import cn.slainc.changeaudit.metrics.ChangeAuditMetrics;
import cn.slainc.changeaudit.spi.ChangeRecorder;
import cn.slainc.changeaudit.spi.ChangeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Pattern;

public class JdbcChangeRecorder implements ChangeRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcChangeRecorder.class);

    // 表名直接拼进 SQL，只接受普通标识符（可带库名前缀），防止配置写坏变成注入面
    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final JdbcTemplate jdbcTemplate;

    private final String table;

    private final ChangeAuditMetrics metrics;

    public JdbcChangeRecorder(JdbcTemplate jdbcTemplate, String table) {
        this(jdbcTemplate, table, new ChangeAuditMetrics());
    }

    public JdbcChangeRecorder(JdbcTemplate jdbcTemplate, String table, ChangeAuditMetrics metrics) {
        if (table == null || !TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("变更审计表名不合法：" + table);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.table = table;
        this.metrics = metrics;
    }

    public ChangeAuditMetrics metrics() {
        return metrics;
    }

    @Override
    public void record(ChangeRecord change) {
        if (change == null) {
            return;
        }
        try {
            jdbcTemplate.update("INSERT INTO " + table
                            + " (entity_type, entity_id, entity_name, operation, changes, changed_fields, operator,"
                            + " operator_id, request_uri, trace_id, change_source, change_time, tenant_id, create_time)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    change.entityType(), change.entityId(), change.entityName(), change.operation(), change.changes(),
                    change.changedFields(), change.operator(), change.operatorId(), change.requestUri(),
                    change.traceId(), change.changeSource(), epochMillis(change.changeTime()), change.tenantId(),
                    System.currentTimeMillis());
            metrics.recordSuccess();
        } catch (RuntimeException exception) {
            metrics.recordFailure();
            // 审计落库失败不能反噬业务：只记错误日志，业务改动照常提交
            LOGGER.error("变更审计落库失败，实体={} 主键={} 原因={}", change.entityType(), change.entityId(),
                    exception.getMessage(), exception);
        }
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? System.currentTimeMillis()
                : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
