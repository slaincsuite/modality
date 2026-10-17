package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.service.policy.SecurityPolicyService;
import cn.slainc.modules.auth.service.user.TargetUserGuard;
import cn.slainc.modules.auth.store.SecurityPolicyStore;
import cn.slainc.modules.auth.web.policy.SecurityPolicyController;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.spi.AccountAdminPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        "cn.slainc.security.autoconfigure.SecurityAutoConfiguration"
})
@AutoConfigureBefore(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "modality.auth.security-policy", name = "enabled", matchIfMissing = true)
@Import(SecurityPolicyController.class)
public class AuthSecurityPolicyAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthSecurityPolicyAutoConfiguration.class);

    private static final List<String> GLOBAL_TABLES = List.of(AuthKeys.SECURITY_POLICY_TABLE,
            AuthKeys.FEISHU_APP_CONFIG_TABLE, AuthKeys.THIRD_SYNC_RECORD_TABLE);

    @Bean
    @ConditionalOnMissingBean(name = "authSchemaInitializer")
    @ConditionalOnBean(DataSource.class)
    public ClasspathSchemaInitializer authSchemaInitializer(DataSource dataSource) {
        return new ClasspathSchemaInitializer(dataSource, "auth-schema.sql");
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityPolicyStore securityPolicyStore(JdbcTemplate jdbcTemplate) {
        return new SecurityPolicyStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityPolicyService securityPolicyService(SecurityPolicyStore store, SecurityProperties properties,
                                                       ObjectProvider<DynamicConfigService> dynamicConfig,
                                                       ObjectProvider<StringRedisTemplate> redis) {
        return new SecurityPolicyService(store, properties, dynamicConfig, redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public TargetUserGuard targetUserGuard(ObjectProvider<AccountAdminPort> accountAdminPort) {
        return new TargetUserGuard(accountAdminPort);
    }

    @Bean
    @ConditionalOnMissingBean(name = "securityPolicyBootstrap")
    public ApplicationRunner securityPolicyBootstrap(SecurityPolicyService policyService) {
        return args -> {
            try {
                policyService.applySessionPolicy();
            } catch (RuntimeException e) {
                log.warn("初始化安全策略失败，将使用既有安全配置：{}", e.getMessage());
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "cn.slainc.mybatis.properties.MybatisProperties",
            "cn.slainc.mybatis.tenant.DefaultTenantLineHandler"
    })
    static class MybatisWhitelistConfiguration {

        @Bean
        @ConditionalOnMissingBean(AuthTableWhitelist.class)
        public AuthTableWhitelist authTableWhitelist(ObjectProvider<MybatisProperties> mybatisProperties,
                                                     ObjectProvider<DefaultTenantLineHandler> tenantLineHandler) {
            return new AuthTableWhitelist(GLOBAL_TABLES, mybatisProperties.getIfAvailable(),
                    tenantLineHandler.getIfAvailable());
        }
    }
}
