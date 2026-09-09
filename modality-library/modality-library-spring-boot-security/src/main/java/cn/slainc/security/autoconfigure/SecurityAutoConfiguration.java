package cn.slainc.security.autoconfigure;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.security.annotation.RequireRole;
import cn.slainc.security.annotation.RequireSafe;
import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.ban.AccountBanService;
import cn.slainc.security.cache.SecurityPermissionCache;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.handler.RequireLoginHandler;
import cn.slainc.security.handler.RequirePermissionHandler;
import cn.slainc.security.handler.RequireRoleHandler;
import cn.slainc.security.handler.RequireSafeHandler;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.lock.AccountLockService;
import cn.slainc.security.login.CurrentRequestReader;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.security.metrics.SecurityCounters;
import cn.slainc.security.metrics.SecurityMetricsPort;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.restrict.LoginRestrictionService;
import cn.slainc.security.safe.SafeAuthService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountPasswordUpdater;
import cn.slainc.security.spi.ConfigAccountAuthProvider;
import cn.slainc.security.spi.SecurityPermissionProvider;
import cn.slainc.security.spi.SecurityPolicyProvider;
import cn.slainc.security.stp.SecurityStpLogic;
import cn.slainc.security.stp.StpInterfaceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@AutoConfiguration
@AutoConfigureAfter(name = {"org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"})
@ConditionalOnProperty(prefix = "modality.security", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityPermissionProvider.class)
    public SecurityPermissionProvider securityPermissionProvider() {
        return new EmptyPermissionProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityPolicyProvider.class)
    public SecurityPolicyProvider securityPolicyProvider() {
        return new EmptyPolicyProvider();
    }

    @Bean
    @ConditionalOnMissingBean(AccountAuthProvider.class)
    @ConditionalOnProperty(prefix = "modality.security", name = "account-provider.enabled", matchIfMissing = true)
    public ConfigAccountAuthProvider configAccountAuthProvider(SecurityProperties properties) {
        return new ConfigAccountAuthProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityConfigReader.class)
    public SecurityConfigReader securityConfigReader(SecurityProperties properties,
                                                     ObjectProvider<DynamicConfigService> configService) {
        return new SecurityConfigReader(properties, configService);
    }

    @Bean
    @ConditionalOnMissingBean(name = "adminStpLogic")
    public SecurityStpLogic adminStpLogic() {
        return new SecurityStpLogic(StpKit.TYPE_ADMIN);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "userStpLogic")
    public SecurityStpLogic userStpLogic() {
        return new SecurityStpLogic(StpKit.TYPE_USER);
    }

    @Bean
    @ConditionalOnMissingBean(name = "clientStpLogic")
    public SecurityStpLogic clientStpLogic() {
        return new SecurityStpLogic(StpKit.TYPE_CLIENT);
    }

    @Bean
    public StpKitInitializer stpKitInitializer(List<SecurityStpLogic> logics) {
        return new StpKitInitializer(logics);
    }

    public static class EmptyPermissionProvider implements SecurityPermissionProvider {

        @Override
        public List<String> getPermissions(Object loginId, LoginType type) {
            return List.of();
        }

        @Override
        public List<String> getRoles(Object loginId, LoginType type) {
            return List.of();
        }
    }

    public static class EmptyPolicyProvider implements SecurityPolicyProvider {

        @Override
        public List<String> getPermissions(Object loginId, LoginType type) {
            return List.of();
        }

        @Override
        public List<String> getRoles(Object loginId, LoginType type) {
            return List.of();
        }
    }

    public static class StpKitInitializer {

        public StpKitInitializer(List<SecurityStpLogic> logics) {
            for (SecurityStpLogic logic : logics) {
                StpKit.assign(logic);
            }
        }
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(SecurityPermissionCache.class)
    @ConditionalOnProperty(prefix = "modality.security", name = "permission-cache.enabled", matchIfMissing = true)
    public SecurityPermissionCache securityPermissionCache(StringRedisTemplate redisTemplate,
                                                           SecurityPermissionProvider permissionProvider,
                                                           SecurityPolicyProvider policyProvider,
                                                           SecurityConfigReader config) {
        return new SecurityPermissionCache(redisTemplate, permissionProvider, policyProvider, config);
    }

    @Bean
    @ConditionalOnBean(SecurityPermissionCache.class)
    @ConditionalOnMissingBean(StpInterface.class)
    public StpInterfaceImpl stpInterfaceImpl(SecurityPermissionCache cache) {
        return new StpInterfaceImpl(cache);
    }

    @Bean
    @ConditionalOnMissingBean(RequireLoginHandler.class)
    public RequireLoginHandler requireLoginHandler() {
        return new RequireLoginHandler();
    }

    @Bean
    @ConditionalOnMissingBean(RequireRoleHandler.class)
    public RequireRoleHandler requireRoleHandler() {
        return new RequireRoleHandler();
    }

    @Bean
    @ConditionalOnMissingBean(RequirePermissionHandler.class)
    public RequirePermissionHandler requirePermissionHandler() {
        return new RequirePermissionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(RequireSafeHandler.class)
    public RequireSafeHandler requireSafeHandler() {
        return new RequireSafeHandler();
    }

    @Bean
    @ConditionalOnMissingBean(CurrentRequestReader.class)
    public CurrentRequestReader currentRequestReader() {
        return new CurrentRequestReader();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityAuditRecorder.class)
    public SecurityAuditRecorder securityAuditRecorder(ApplicationEventPublisher publisher) {
        return new SecurityAuditRecorder(publisher);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(AccountLockService.class)
    @ConditionalOnProperty(prefix = "modality.security", name = "lockout.enabled", matchIfMissing = true)
    public AccountLockService accountLockService(StringRedisTemplate redisTemplate, SecurityConfigReader config,
                                                 SecurityAuditRecorder recorder,
                                                 ApplicationEventPublisher publisher) {
        return new AccountLockService(redisTemplate, config, recorder, publisher);
    }

    @Bean
    @ConditionalOnMissingBean(AccountBanService.class)
    @ConditionalOnProperty(prefix = "modality.security", name = "ban.enabled", matchIfMissing = true)
    public AccountBanService accountBanService(SecurityAuditRecorder recorder) {
        return new AccountBanService(recorder);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(KickService.class)
    @ConditionalOnProperty(prefix = "modality.security", name = "kick.enabled", matchIfMissing = true)
    public KickService kickService(StringRedisTemplate redisTemplate, SecurityConfigReader config,
                                   SecurityAuditRecorder recorder, ObjectProvider<SecurityMetricsPort> metrics) {
        return new KickService(redisTemplate, config, recorder, metrics.getIfAvailable(SecurityCounters::new));
    }

    @Bean
    @ConditionalOnMissingBean(LoginRestrictionService.class)
    @ConditionalOnProperty(prefix = "modality.security", name = "restriction.enabled", matchIfMissing = true)
    public LoginRestrictionService loginRestrictionService(SecurityConfigReader config) {
        return new LoginRestrictionService(config);
    }

    @Bean
    @ConditionalOnMissingBean(SafeAuthService.class)
    @ConditionalOnProperty(prefix = "modality.security", name = "safe.enabled", matchIfMissing = true)
    public SafeAuthService safeAuthService() {
        return new SafeAuthService();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(PasswordPolicyService.class)
    @ConditionalOnProperty(prefix = "modality.security", name = "password.enabled", matchIfMissing = true)
    public PasswordPolicyService passwordPolicyService(SecurityConfigReader config,
                                                       ObjectProvider<AccountAuthProvider> authProvider,
                                                       ObjectProvider<AccountPasswordUpdater> passwordUpdater,
                                                       StringRedisTemplate redisTemplate) {
        return new PasswordPolicyService(config, authProvider, passwordUpdater, redisTemplate);
    }

    @Bean
    @ConditionalOnBean({SecurityPermissionCache.class, AccountLockService.class})
    @ConditionalOnMissingBean(SecurityLoginService.class)
    @ConditionalOnProperty(prefix = "modality.security", name = "login.enabled", matchIfMissing = true)
    public SecurityLoginService securityLoginService(SecurityConfigReader config,
                                                     ObjectProvider<AccountAuthProvider> authProvider,
                                                     AccountBanService banService, AccountLockService lockService,
                                                     LoginRestrictionService restrictionService,
                                                     PasswordPolicyService passwordPolicy, KickService kickService,
                                                     SecurityAuditRecorder recorder,
                                                     ObjectProvider<SecurityMetricsPort> metrics,
                                                     ApplicationEventPublisher publisher,
                                                     CurrentRequestReader requestReader,
                                                     StringRedisTemplate redisTemplate) {
        return new SecurityLoginService(config, authProvider, banService, lockService, restrictionService,
                passwordPolicy, kickService, recorder, metrics.getIfAvailable(SecurityCounters::new), publisher,
                requestReader, redisTemplate);
    }
}
