package cn.slainc.modules.audit.store;

import cn.slainc.modules.audit.service.AuditLogService;

import cn.slainc.logger.oplog.AuditLogStore;
import cn.slainc.logger.oplog.AuditRecord;
import cn.slainc.modules.audit.entity.AuditLogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// logger 的 @AuditLog 切面在本模块对接落库：完整保留 AuditRecord 全字段（含前后快照与链路追踪）
public class JdbcAuditLogStore implements AuditLogStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditLogStore.class);

    private static final int TEXT_MAX_LENGTH = 4000;

    private final AuditLogService auditLogService;

    public JdbcAuditLogStore(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void save(AuditRecord record) {
        try {
            AuditLogRecord row = new AuditLogRecord();
            row.setAuditId(record.id());
            row.setCategory(record.category() == null ? "DATA" : record.category().name());
            row.setOperation(record.operation() == null ? "OTHER" : record.operation().name());
            row.setLevel(record.level() == null ? "INFO" : record.level().name());
            row.setModule(record.module());
            row.setDescription(truncate(record.description(), 500));
            row.setSuccess(record.success());
            row.setUserId(record.userId());
            row.setUsername(record.username());
            row.setLoginId(record.loginId());
            row.setTenantId(record.tenantId());
            row.setClientIp(record.clientIp());
            row.setUserAgent(truncate(record.userAgent(), 256));
            row.setDeviceId(record.deviceId());
            row.setRequestPath(truncate(record.requestPath(), 256));
            row.setHttpMethod(record.httpMethod());
            row.setTarget(truncate(record.target(), 256));
            row.setParams(truncate(record.params(), TEXT_MAX_LENGTH));
            row.setResult(truncate(record.result(), TEXT_MAX_LENGTH));
            row.setBeforeSnapshot(truncate(record.beforeSnapshot(), TEXT_MAX_LENGTH));
            row.setAfterSnapshot(truncate(record.afterSnapshot(), TEXT_MAX_LENGTH));
            row.setExceptionClass(truncate(record.exceptionClass(), 256));
            row.setExceptionMessage(truncate(record.exceptionMessage(), 1000));
            row.setCostMillis(record.costMillis());
            row.setTraceId(record.traceId());
            row.setCreateTime(record.timestamp() == null ? System.currentTimeMillis()
                    : record.timestamp().toEpochMilli());
            auditLogService.insert(row);
        } catch (Exception e) {
            // 审计落库是旁路能力，失败只告警不抛出
            log.warn("审计日志落库失败: {} {}", e.getClass().getName(), e.getMessage());
        }
    }

    @Override
    public String name() {
        return "audit-jdbc-log-store";
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
