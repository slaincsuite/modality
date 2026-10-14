package cn.slainc.modules.audit.listener;

import cn.slainc.modules.audit.service.oplog.AuditLogService;

import cn.slainc.core.context.RequestContext;
import cn.slainc.core.event.ConfigChangedEvent;
import cn.slainc.core.event.ConsentGrantedEvent;
import cn.slainc.core.event.LoginEvent;
import cn.slainc.core.event.LogoutEvent;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLevel;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.audit.entity.AuditLogRecord;
import cn.slainc.security.login.CurrentRequestReader;
import cn.slainc.session.event.SessionKickedEvent;
import java.util.StringJoiner;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;


// 领域事件到审计日志的桥：登录/注销/授权/配置变更/踢人五类事件旁路落库，失败不影响主链路
public class AuditEventCollector {

    private static final Logger log = LoggerFactory.getLogger(AuditEventCollector.class);

    private final AuditLogService auditLogService;

    private final CurrentRequestReader requestReader;

    public AuditEventCollector(AuditLogService auditLogService, CurrentRequestReader requestReader) {
        this.auditLogService = auditLogService;
        this.requestReader = requestReader;
    }

    @EventListener
    public void onLogin(LoginEvent event) {
        AuditLogRecord record = base(AuditCategory.LOGIN, AuditOperation.LOGIN,
                event.isSucceeded() ? AuditLevel.INFO : AuditLevel.WARN, "auth");
        record.setUserId(event.getUserId());
        record.setUsername(event.getUsername());
        if (event.getLoginType() != null) {
            record.setLoginType(event.getLoginType().value());
        }
        record.setSuccess(event.isSucceeded());
        record.setClientIp(event.getClientIp() != null ? event.getClientIp() : safeClientIp());
        if (event.isSucceeded()) {
            record.setDescription("用户登录成功");
        } else {
            record.setDescription("用户登录失败：" + event.getFailureReason());
        }
        record.setTenantId(event.getTenantId());
        auditLogService.insert(record);
    }

    @EventListener
    public void onLogout(LogoutEvent event) {
        AuditLogRecord record = base(AuditCategory.LOGIN, AuditOperation.LOGOUT, AuditLevel.INFO, "auth");
        record.setUserId(event.getUserId());
        record.setUsername(event.getUsername());
        if (event.getLoginType() != null) {
            record.setLoginType(event.getLoginType().value());
        }
        record.setDescription(event.isForced() ? "用户被强制登出" : "用户主动登出");
        record.setTenantId(event.getTenantId());
        auditLogService.insert(record);
    }

    @EventListener
    public void onConsentGranted(ConsentGrantedEvent event) {
        AuditLogRecord record = base(AuditCategory.PERMISSION, AuditOperation.GRANT, AuditLevel.WARN, "oauth2");
        record.setUserId(event.getUserId());
        StringJoiner scopes = new StringJoiner(", ");
        event.getScopes().forEach(scopes::add);
        record.setDescription("用户授权应用 " + event.getClientId() + "，scope=" + scopes);
        record.setTarget(event.getClientId());
        record.setResult(scopes.toString());
        record.setTenantId(event.getTenantId());
        auditLogService.insert(record);
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        AuditLogRecord record = base(AuditCategory.SYSTEM, AuditOperation.CONFIG, AuditLevel.WARN, "config");
        record.setDescription("配置变更：" + event.getConfigKey());
        record.setTarget(event.getConfigKey());
        record.setBeforeSnapshot(event.getOldValue());
        record.setAfterSnapshot(event.getNewValue());
        record.setSuccess(!event.isRemoved());
        record.setTenantId(event.getTenantId());
        auditLogService.insert(record);
    }

    @EventListener
    public void onSessionKicked(SessionKickedEvent event) {
        AuditLogRecord record = base(AuditCategory.SECURITY, AuditOperation.LOGOUT, AuditLevel.WARN, "session");
        resolveSubject(record, event.getLoginId());
        if (event.getLoginType() != null) {
            record.setLoginType(event.getLoginType());
        }
        record.setDescription("会话被强制下线：" + event.getReason());
        record.setTarget(event.getTokenValue());
        auditLogService.insert(record);
    }

    private void resolveSubject(AuditLogRecord record, String loginId) {
        if (loginId == null) {
            return;
        }
        record.setLoginId(loginId);
        try {
            record.setUserId(Long.valueOf(loginId));
        } catch (NumberFormatException e) {
            record.setUsername(loginId);
        }
    }

    private AuditLogRecord base(AuditCategory category, AuditOperation operation, AuditLevel level, String module) {
        AuditLogRecord record = new AuditLogRecord();
        record.setAuditId(UUID.randomUUID().toString());
        record.setCategory(category.name());
        record.setOperation(operation.name());
        record.setLevel(level.name());
        record.setModule(module);
        record.setSuccess(true);
        record.setTraceId(RequestContext.traceId());
        record.setCreateTime(System.currentTimeMillis());
        return record;
    }

    private String safeClientIp() {
        try {
            return requestReader != null ? requestReader.clientIp() : null;
        } catch (Exception e) {
            log.debug("读取客户端 IP 失败：{}", e.getMessage());
            return null;
        }
    }
}
