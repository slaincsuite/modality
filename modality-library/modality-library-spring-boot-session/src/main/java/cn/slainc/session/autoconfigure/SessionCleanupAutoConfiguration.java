package cn.slainc.session.autoconfigure;

import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.kick.KickService;
import cn.slainc.session.cleanup.SessionCleanupScheduler;
import cn.slainc.session.cleanup.SessionCleanupService;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.query.SessionScanner;
import cn.slainc.session.timeout.SessionTimeoutPolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(value = SessionAutoConfiguration.class,
        name = "cn.slainc.security.autoconfigure.SecurityAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.session", name = "cleanup.enabled", matchIfMissing = false)
public class SessionCleanupAutoConfiguration {

    @Bean
    @ConditionalOnBean(KickService.class)
    @ConditionalOnMissingBean(SessionCleanupService.class)
    public SessionCleanupService sessionCleanupService(SessionConfigReader config, SessionTimeoutPolicy policy,
                                                       SessionScanner scanner, KickService kickService,
                                                       SecurityAuditRecorder recorder,
                                                       ApplicationEventPublisher publisher) {
        return new SessionCleanupService(config, policy, scanner, kickService, recorder, publisher);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(SessionCleanupService.class)
    @ConditionalOnMissingBean(SessionCleanupScheduler.class)
    public SessionCleanupScheduler sessionCleanupScheduler(SessionCleanupService cleanupService,
                                                           SessionConfigReader config) {
        return new SessionCleanupScheduler(cleanupService, config);
    }
}
