package cn.slainc.modules.audit.autoconfigure;

import cn.slainc.modules.audit.scheduler.AuditAlertScheduler;
import cn.slainc.modules.audit.service.alert.AuditAlertService;
import cn.slainc.modules.audit.scheduler.AuditCleanupScheduler;
import cn.slainc.modules.audit.service.cleanup.AuditCleanupService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.audit", name = "enabled", matchIfMissing = true)
public class AuditSchedulerAutoConfiguration {

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean(AuditCleanupScheduler.class)
    @ConditionalOnProperty(prefix = "modality.audit.cleanup", name = "enabled", matchIfMissing = true)
    public AuditCleanupScheduler auditCleanupScheduler(AuditCleanupService cleanupService,
                                                       AuditProperties properties) {
        return new AuditCleanupScheduler(cleanupService, properties.getCleanup().getIntervalSeconds());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean(AuditAlertScheduler.class)
    @ConditionalOnProperty(prefix = "modality.audit.alert", name = "enabled", matchIfMissing = true)
    public AuditAlertScheduler auditAlertScheduler(AuditAlertService alertService, AuditProperties properties) {
        return new AuditAlertScheduler(alertService, properties.getAlert().getEvaluateIntervalSeconds());
    }
}
