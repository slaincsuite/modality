package cn.slainc.modules.audit.service;

import cn.slainc.modules.audit.config.AuditConfigReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;


// 审计数据定时清理：按保留天数（热配置）删除三张审计表的历史数据，删除是物理删除、不可恢复
public class AuditCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AuditCleanupService.class);

    private final AuditLogService auditLogService;

    private final ApiLogService apiLogService;

    // 策略评估日志依赖 policy 库（可选）
    private final ObjectProvider<PolicyEvaluationLogService> policyLogService;

    private final AuditConfigReader configReader;

    public AuditCleanupService(AuditLogService auditLogService, ApiLogService apiLogService,
                               ObjectProvider<PolicyEvaluationLogService> policyLogService,
                               AuditConfigReader configReader) {
        this.auditLogService = auditLogService;
        this.apiLogService = apiLogService;
        this.policyLogService = policyLogService;
        this.configReader = configReader;
    }

    public Map<String, Object> cleanup() {
        int retentionDays = configReader.cleanupRetentionDays();
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("保留天数必须大于 0，当前值：" + retentionDays);
        }
        long boundary = LocalDateTime.now().minusDays(retentionDays)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("retentionDays", retentionDays);
        report.put("boundary", boundary);
        report.put("auditLogs", purgeAuditLogs(boundary));
        report.put("apiLogs", apiLogService.purgeBefore(boundary));
        PolicyEvaluationLogService policyService = policyLogService.getIfAvailable();
        report.put("policyLogs", policyService == null ? 0 : policyService.purgeBefore(boundary));
        log.info("审计数据清理完成：保留 {} 天，{}", retentionDays, report);
        return report;
    }

    private int purgeAuditLogs(long boundary) {
        // AuditLogService 的 insert 是旁路容错的，这里清理同样只告警不抛出
        try {
            return auditLogService.purgeBefore(boundary);
        } catch (Exception e) {
            log.warn("审计日志清理失败: {} {}", e.getClass().getName(), e.getMessage());
            return 0;
        }
    }
}
