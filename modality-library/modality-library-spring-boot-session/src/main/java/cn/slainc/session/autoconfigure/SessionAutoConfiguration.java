package cn.slainc.session.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.config.SessionProperties;
import cn.slainc.session.extend.SessionExtender;
import cn.slainc.session.extend.SessionRenewalService;
import cn.slainc.session.online.OnlineUserService;
import cn.slainc.session.query.SessionQueryService;
import cn.slainc.session.query.SessionScanner;
import cn.slainc.session.stats.SessionStatsListener;
import cn.slainc.session.stats.SessionStatsService;
import cn.slainc.session.timeout.SessionTimeoutPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(name = "cn.slainc.security.autoconfigure.SecurityAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.session", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SessionProperties.class)
public class SessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SessionConfigReader.class)
    public SessionConfigReader sessionConfigReader(SessionProperties properties,
                                                   ObjectProvider<DynamicConfigService> configService,
                                                   SecurityConfigReader securityConfig) {
        return new SessionConfigReader(properties, configService, securityConfig);
    }

    @Bean
    @ConditionalOnMissingBean(SessionScanner.class)
    public SessionScanner sessionScanner(SessionConfigReader config, SecurityConfigReader securityConfig) {
        return new SessionScanner(config, securityConfig);
    }

    @Bean
    @ConditionalOnMissingBean(SessionTimeoutPolicy.class)
    public SessionTimeoutPolicy sessionTimeoutPolicy(SessionConfigReader config) {
        return new SessionTimeoutPolicy(config);
    }

    @Bean
    @ConditionalOnMissingBean(SessionQueryService.class)
    @ConditionalOnProperty(prefix = "modality.session", name = "query.enabled", matchIfMissing = true)
    public SessionQueryService sessionQueryService(SessionScanner scanner) {
        return new SessionQueryService(scanner);
    }

    @Bean
    @ConditionalOnMissingBean(OnlineUserService.class)
    @ConditionalOnProperty(prefix = "modality.session", name = "online.enabled", matchIfMissing = true)
    public OnlineUserService onlineUserService(SessionScanner scanner, SessionConfigReader config) {
        return new OnlineUserService(scanner, config);
    }

    @Bean
    @ConditionalOnMissingBean(SessionStatsService.class)
    @ConditionalOnProperty(prefix = "modality.session", name = "stats.enabled", matchIfMissing = true)
    public SessionStatsService sessionStatsService(SessionScanner scanner,
                                                   ObjectProvider<SessionStatsListener> listeners) {
        return new SessionStatsService(scanner, listeners);
    }

    @Bean
    @ConditionalOnMissingBean(SessionExtender.class)
    @ConditionalOnProperty(prefix = "modality.session", name = "extend.enabled", matchIfMissing = true)
    public SessionExtender sessionExtender() {
        return new SessionExtender();
    }

    @Bean
    @ConditionalOnMissingBean(SessionRenewalService.class)
    @ConditionalOnProperty(prefix = "modality.session", name = "renew.enabled", matchIfMissing = true)
    public SessionRenewalService sessionRenewalService(SessionConfigReader config) {
        return new SessionRenewalService(config);
    }
}
