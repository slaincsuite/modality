package cn.slainc.modules.audit.service.alert;

import cn.slainc.modules.audit.service.oplog.AuditLogService;

import cn.slainc.modules.audit.config.AuditConfigReader;
import cn.slainc.modules.audit.alert.AuditAlertChannel;
import cn.slainc.modules.audit.alert.AuditAlertMessage;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;


// 审计告警评估器：时间窗内风险操作与登录失败次数超阈值时经渠道发送告警（阈值支持热配置）
public class AuditAlertService {

    private static final Logger log = LoggerFactory.getLogger(AuditAlertService.class);

    private final AuditLogService auditLogService;

    private final AuditConfigReader configReader;

    private final ObjectProvider<AuditAlertChannel> channels;

    public AuditAlertService(AuditLogService auditLogService, AuditConfigReader configReader,
                             ObjectProvider<AuditAlertChannel> channels) {
        this.auditLogService = auditLogService;
        this.configReader = configReader;
        this.channels = channels;
    }

    public List<AuditAlertMessage> evaluate() {
        long now = System.currentTimeMillis();
        long windowStart = LocalDateTime.now().minusMinutes(Math.max(1, configReader.properties().getAlert()
                .getWindowMinutes())).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<AuditAlertMessage> alerts = new ArrayList<>();
        long risky = riskyCount(windowStart, now);
        int riskyThreshold = configReader.alertRiskyThreshold();
        if (risky >= riskyThreshold) {
            alerts.add(new AuditAlertMessage("audit-risky-operations", "风险操作数量超阈值", "WARN", risky,
                    riskyThreshold, "次", now, "最近时间窗内风险操作（WARN/ERROR/CRITICAL）共 " + risky
                    + " 次，超过阈值 " + riskyThreshold));
        }
        long loginFailures = auditLogService.count(windowStart, now, false, "LOGIN");
        int failureThreshold = configReader.alertLoginFailureThreshold();
        if (loginFailures >= failureThreshold) {
            alerts.add(new AuditAlertMessage("audit-login-failures", "登录失败次数超阈值", "WARN", loginFailures,
                    failureThreshold, "次", now, "最近时间窗内登录失败共 " + loginFailures + " 次，超过阈值 "
                    + failureThreshold));
        }
        for (AuditAlertMessage alert : alerts) {
            log.warn("审计告警[{}]：{}", alert.ruleId(), alert.message());
            channels.orderedStream().forEach(channel -> {
                try {
                    channel.send(alert);
                } catch (Exception e) {
                    log.warn("审计告警渠道（{}）发送失败：{}", channel.name(), e.getMessage());
                }
            });
        }
        return alerts;
    }

    private long riskyCount(long from, long to) {
        return auditLogService.countLevelIn(from, to, List.of("WARN", "ERROR", "CRITICAL"));
    }
}
