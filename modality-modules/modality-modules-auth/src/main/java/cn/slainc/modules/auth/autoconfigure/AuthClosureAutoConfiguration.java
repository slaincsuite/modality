package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.modules.auth.web.AccountClosureController;
import cn.slainc.modules.auth.scheduler.AccountClosureScheduler;
import cn.slainc.modules.auth.service.AccountClosureService;
import cn.slainc.modules.auth.store.AccountClosureStore;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.spi.AccountAdminPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "modality.auth.closure", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AuthProperties.class)
@Import(AccountClosureController.class)
public class AuthClosureAutoConfiguration {

    @Bean
    // 多个模块的初始化器同为 ClasspathSchemaInitializer 类型，必须按 bean 名判重，避免跨模块误判导致建表被跳过
    @ConditionalOnMissingBean(name = "authSchemaInitializer")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "modality.auth.closure", name = "auto-create-table", matchIfMissing = true)
    public ClasspathSchemaInitializer authSchemaInitializer(DataSource dataSource) {
        return new ClasspathSchemaInitializer(dataSource, "cn/slainc/modules/auth/auth-schema.sql");
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountClosureStore accountClosureStore(JdbcTemplate jdbcTemplate) {
        return new AccountClosureStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountClosureService accountClosureService(AccountClosureStore store, AuthProperties properties,
                                                       ObjectProvider<AccountAdminPort> adminPort,
                                                       ObjectProvider<SecurityAuditRecorder> recorder) {
        return new AccountClosureService(store, properties, adminPort, recorder);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public AccountClosureScheduler accountClosureScheduler(AccountClosureService closureService,
                                                           AuthProperties properties) {
        return new AccountClosureScheduler(closureService, properties);
    }
}
