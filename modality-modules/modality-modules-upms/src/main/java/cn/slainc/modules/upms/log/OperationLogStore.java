package cn.slainc.modules.upms.log;

import cn.slainc.logger.audit.AuditRecord;
import cn.slainc.logger.audit.AuditLogStore;
import cn.slainc.modules.upms.entity.UserOperationLogRecord;
import cn.slainc.modules.upms.mapper.UserOperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 用户操作日志落库实现：接 logger 模块的审计分发器，写入 t_user_operation_log 供查询与导出
public class OperationLogStore implements AuditLogStore {

    private static final Logger log = LoggerFactory.getLogger(OperationLogStore.class);

    private static final int DETAIL_MAX_LENGTH = 2000;

    private final UserOperationLogMapper operationLogMapper;

    public OperationLogStore(UserOperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void save(AuditRecord record) {
        try {
            UserOperationLogRecord row = new UserOperationLogRecord();
            row.setTenantId(record.tenantId() == null ? 1L : record.tenantId());
            row.setAuditId(record.id());
            row.setUserId(record.userId());
            row.setUsername(record.username());
            row.setModuleName(record.module());
            row.setOperation(record.operation() == null ? null : record.operation().name());
            row.setDescription(record.description());
            row.setSuccess(record.success());
            row.setClientIp(record.clientIp());
            row.setUserAgent(truncate(record.userAgent(), 256));
            row.setRequestPath(record.requestPath());
            row.setHttpMethod(record.httpMethod());
            row.setCostMillis(record.costMillis());
            row.setDetail(composeDetail(record));
            row.setCreateTime(System.currentTimeMillis());
            operationLogMapper.insert(row);
        } catch (Exception e) {
            // 操作日志是旁路能力，落库失败只告警不抛出
            log.warn("用户操作日志落库失败: {} {}", e.getClass().getName(), e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "upms-jdbc-operation-log";
    }

    private String composeDetail(AuditRecord record) {
        StringBuilder detail = new StringBuilder();
        if (record.target() != null) {
            detail.append("target=").append(record.target()).append(';');
        }
        if (record.params() != null) {
            detail.append("params=").append(truncate(record.params(), 800)).append(';');
        }
        if (record.result() != null) {
            detail.append("result=").append(truncate(record.result(), 600)).append(';');
        }
        if (record.exceptionMessage() != null) {
            detail.append("error=").append(truncate(record.exceptionMessage(), 400)).append(';');
        }
        return detail.length() > DETAIL_MAX_LENGTH ? detail.substring(0, DETAIL_MAX_LENGTH) : detail.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
