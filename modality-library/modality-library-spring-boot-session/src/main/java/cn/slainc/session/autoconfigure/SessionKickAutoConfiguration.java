package cn.slainc.session.autoconfigure;

import cn.slainc.security.kick.KickService;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.session.kick.KickSessionService;
import cn.slainc.session.kick.SessionKickNotifier;
import cn.slainc.session.kick.Slf4jSessionKickNotifier;
import cn.slainc.session.limit.SessionLimitService;
import cn.slainc.session.limit.SessionLoginListener;
import cn.slainc.session.query.SessionScanner;
import cn.slainc.session.config.SessionConfigReader;
import org.springframework.beans.factory.ObjectProvider;
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
@ConditionalOnProperty(prefix = "modality.session", name = "kick.enabled", matchIfMissing = true)
public class SessionKickAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SessionKickNotifier.class)
    @ConditionalOnProperty(prefix = "modality.session", name = "notify.enabled", matchIfMissing = true)
    public Slf4jSessionKickNotifier slf4jSessionKickNotifier() {
        return new Slf4jSessionKickNotifier();
    }

    @Bean
    @ConditionalOnBean({KickService.class, SecurityLoginService.class})
    @ConditionalOnMissingBean(KickSessionService.class)
    public KickSessionService kickSessionService(KickService kickService, SecurityLoginService loginService,
                                                 SessionScanner scanner, ApplicationEventPublisher publisher,
                                                 ObjectProvider<SessionKickNotifier> notifiers) {
        return new KickSessionService(kickService, loginService, scanner, publisher, notifiers);
    }

    @Bean
    @ConditionalOnBean(KickSessionService.class)
    @ConditionalOnMissingBean(SessionLimitService.class)
    @ConditionalOnProperty(prefix = "modality.session", name = "limit.enabled", matchIfMissing = true)
    public SessionLimitService sessionLimitService(SessionConfigReader config,
                                                   KickSessionService kickSessionService) {
        return new SessionLimitService(config, kickSessionService);
    }

    @Bean
    @ConditionalOnBean(SessionLimitService.class)
    @ConditionalOnMissingBean(SessionLoginListener.class)
    @ConditionalOnProperty(prefix = "modality.session", name = "limit.enabled", matchIfMissing = true)
    public SessionLoginListener sessionLoginListener(SessionLimitService limitService) {
        return new SessionLoginListener(limitService);
    }
}
