package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.mfa.authentication.SecondFactorGate;
import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.mfa.recovery.RecoveryCodeGenerator;
import cn.slainc.mfa.spi.MfaFactorPort;
import cn.slainc.mfa.spi.MfaUserLoader;
import cn.slainc.mfa.support.MfaSecretCodec;
import cn.slainc.mfa.totp.TotpEngine;
import cn.slainc.modules.auth.mfa.AuthMfaUserLoader;
import cn.slainc.modules.auth.mfa.MfaLoginTicket;
import cn.slainc.modules.auth.service.login.AuthLoginService;
import cn.slainc.modules.auth.service.mfa.AuthMfaService;
import cn.slainc.modules.auth.service.mfa.AuthSecondFactorGate;
import cn.slainc.modules.auth.service.policy.SecurityPolicyService;
import cn.slainc.modules.auth.service.user.TargetUserGuard;
import cn.slainc.modules.auth.service.session.AuthSessionService;
import cn.slainc.modules.auth.store.AuthTicketStore;
import cn.slainc.modules.auth.store.JdbcMfaFactorStore;
import cn.slainc.modules.auth.web.mfa.AuthMfaController;
import cn.slainc.modules.auth.web.mfa.UserMfaManageController;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountAdminPort;
import cn.slainc.security.spi.SecurityPermissionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

// 未引入 mfa 库（compileOnly）或关闭 modality.mfa.enabled 时整体退避，auth 模块可独立启动
@AutoConfiguration(afterName = {"cn.slainc.mfa.autoconfigure.MfaAutoConfiguration",
        "cn.slainc.security.autoconfigure.SecurityAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"})
@ConditionalOnClass(name = {"cn.slainc.mfa.autoconfigure.MfaAutoConfiguration",
        "org.springframework.jdbc.core.JdbcTemplate"})
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "modality.mfa", name = "enabled", matchIfMissing = true)
@Import({AuthMfaController.class, UserMfaManageController.class})
public class AuthMfaAutoConfiguration {

    @Bean
    // 与注销模块的 authSchemaInitializer 同脚本幂等建表，仍按 bean 名判重避免重复初始化器
    @ConditionalOnMissingBean(name = "mfaSchemaInitializer")
    @ConditionalOnBean(DataSource.class)
    public ClasspathSchemaInitializer mfaSchemaInitializer(DataSource dataSource) {
        return new ClasspathSchemaInitializer(dataSource, "auth-schema.sql");
    }

    @Bean
    @ConditionalOnMissingBean(MfaFactorPort.class)
    public JdbcMfaFactorStore mfaFactorStore(JdbcTemplate jdbcTemplate) {
        return new JdbcMfaFactorStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public TargetUserGuard targetUserGuard(ObjectProvider<AccountAdminPort> accountAdminPort) {
        return new TargetUserGuard(accountAdminPort);
    }

    @Bean
    @ConditionalOnMissingBean(MfaUserLoader.class)
    public AuthMfaUserLoader mfaUserLoader(ObjectProvider<AccountAuthProvider> authProvider,
                                           JdbcMfaFactorStore factorStore) {
        return new AuthMfaUserLoader(authProvider, factorStore);
    }

    @Bean
    // 返回类型同为 AuthTicketStore，按 bean 名判重避免与其他票据存储相互误退避
    @ConditionalOnMissingBean(name = "mfaLoginTicketStore")
    public AuthTicketStore<MfaLoginTicket> mfaLoginTicketStore(ObjectProvider<StringRedisTemplate> redis) {
        return AuthTicketStore.of(redis.getIfAvailable(), AuthTicketStore.MFA_KEY_PREFIX,
                MfaLoginTicket::getChallengeId, MfaLoginTicket.class);
    }

    @Bean
    @ConditionalOnMissingBean(AuthSecondFactorGate.class)
    public AuthMfaService authMfaService(MfaConfigReader configReader, TotpEngine totp,
                                         RecoveryCodeGenerator recoveryCodeGenerator, MfaSecretCodec secretCodec,
                                         SecondFactorGate gate, JdbcMfaFactorStore factorStore,
                                         AuthTicketStore<MfaLoginTicket> ticketStore,
                                         AuthSessionService sessionService, AuthLoginService loginService,
                                         ObjectProvider<AccountAuthProvider> authProvider,
                                         ObjectProvider<SecurityPermissionProvider> permissionProvider,
                                         ObjectProvider<SecurityPolicyService> policyService) {
        return new AuthMfaService(configReader, totp, recoveryCodeGenerator, secretCodec, gate, factorStore,
                ticketStore, sessionService, loginService, authProvider, permissionProvider, policyService);
    }
}
