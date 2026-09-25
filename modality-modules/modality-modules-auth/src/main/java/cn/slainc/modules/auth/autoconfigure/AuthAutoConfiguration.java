package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.captcha.CaptchaService;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.auth.login.AuthLoginController;
import cn.slainc.modules.auth.login.AuthLoginService;
import cn.slainc.modules.auth.login.AuthLogoutController;
import cn.slainc.modules.auth.login.AuthLogoutService;
import cn.slainc.modules.auth.login.AuthSessionService;
import cn.slainc.modules.auth.login.LogoutRecordCollector;
import cn.slainc.modules.auth.login.SessionRenewalFilter;
import cn.slainc.modules.auth.password.AuthPasswordController;
import cn.slainc.modules.auth.password.AuthPasswordService;
import cn.slainc.modules.auth.qrcode.MemoryQrLoginTicketStore;
import cn.slainc.modules.auth.qrcode.QrImageService;
import cn.slainc.modules.auth.qrcode.QrLoginController;
import cn.slainc.modules.auth.qrcode.QrLoginService;
import cn.slainc.modules.auth.qrcode.QrLoginTicketStore;
import cn.slainc.modules.auth.qrcode.RedisQrLoginTicketStore;
import cn.slainc.modules.auth.safe.SafeAuthController;
import cn.slainc.modules.upms.service.UserService;
import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.ban.AccountBanService;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.login.CurrentRequestReader;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.session.extend.SessionExtender;
import cn.slainc.session.extend.SessionRenewalService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(afterName = "cn.slainc.security.autoconfigure.SecurityAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.auth", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AuthProperties.class)
@Import({AuthLoginController.class, AuthLogoutController.class, QrLoginController.class,
        AuthPasswordController.class, SafeAuthController.class})
public class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthSessionService authSessionService(SecurityConfigReader config,
                                                 ObjectProvider<AccountAuthProvider> authProvider,
                                                 AccountBanService banService, KickService kickService,
                                                 SecurityAuditRecorder recorder,
                                                 ObjectProvider<PasswordPolicyService> passwordPolicy,
                                                 ApplicationEventPublisher publisher,
                                                 CurrentRequestReader requestReader,
                                                 ObjectProvider<StringRedisTemplate> redis,
                                                 ObjectProvider<BusinessMetrics> metrics) {
        return new AuthSessionService(config, authProvider, banService, kickService, recorder, passwordPolicy,
                publisher, requestReader, redis, metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthLoginService authLoginService(SecurityLoginService delegate, AuthProperties properties,
                                             ObjectProvider<CaptchaService> captchaService,
                                             ObjectProvider<UserService> userService,
                                             ObjectProvider<SessionRenewalService> renewalService,
                                             ObjectProvider<SessionExtender> sessionExtender,
                                             ObjectProvider<CurrentRequestReader> requestReader,
                                             ApplicationEventPublisher publisher) {
        return new AuthLoginService(delegate, properties, captchaService, userService, renewalService,
                sessionExtender, requestReader, publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthLogoutService authLogoutService(ApplicationEventPublisher publisher,
                                               ObjectProvider<AccountAuthProvider> authProvider,
                                               ObjectProvider<cn.slainc.web.websocket.WebSocketBroadcaster> broadcaster) {
        return new AuthLogoutService(publisher, authProvider, broadcaster);
    }

    @Bean
    @ConditionalOnMissingBean
    public LogoutRecordCollector logoutRecordCollector(
            ObjectProvider<cn.slainc.modules.upms.mapper.UserOperationLogMapper> operationLogMapper,
            ObjectProvider<cn.slainc.modules.upms.mapper.UserMapper> userMapper,
            ObjectProvider<CurrentRequestReader> requestReader) {
        return new LogoutRecordCollector(operationLogMapper, userMapper, requestReader);
    }

    @Bean
    @ConditionalOnMissingBean
    public QrImageService qrImageService(AuthProperties properties) {
        return new QrImageService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public QrLoginTicketStore qrLoginTicketStore(ObjectProvider<StringRedisTemplate> redis) {
        StringRedisTemplate template = redis.getIfAvailable();
        return template == null ? new MemoryQrLoginTicketStore() : new RedisQrLoginTicketStore(template);
    }

    @Bean
    @ConditionalOnMissingBean
    public QrLoginService qrLoginService(QrLoginTicketStore store, QrImageService imageService,
                                         AuthProperties properties, AuthSessionService sessionService,
                                         ObjectProvider<AccountAuthProvider> authProvider,
                                         ObjectProvider<cn.slainc.web.websocket.WebSocketBroadcaster> broadcaster) {
        return new QrLoginService(store, imageService, properties, sessionService, authProvider, broadcaster);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthPasswordService authPasswordService(
            ObjectProvider<cn.slainc.modules.upms.service.UserPasswordService> upmsPasswordService,
            PasswordPolicyService passwordPolicy, ObjectProvider<AccountAuthProvider> authProvider) {
        return new AuthPasswordService(upmsPasswordService, passwordPolicy, authProvider);
    }

    @Bean
    @ConditionalOnWebApplication
    public FilterRegistrationBean<SessionRenewalFilter> sessionRenewalFilter(AuthProperties properties,
                                                                              SessionRenewalService renewalService,
                                                                              SessionExtender sessionExtender) {
        FilterRegistrationBean<SessionRenewalFilter> registration = new FilterRegistrationBean<>(
                new SessionRenewalFilter(properties, renewalService, sessionExtender));
        // 紧随 Sa-Token(-6000) 与 UPMS 上下文绑定(-5900) 之后，保证读到的令牌已通过认证
        registration.setOrder(-5800);
        return registration;
    }
}
