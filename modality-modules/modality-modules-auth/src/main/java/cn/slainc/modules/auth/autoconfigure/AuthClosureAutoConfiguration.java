package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.modules.auth.closure.AccountClosureController;
import cn.slainc.modules.auth.closure.AccountClosureScheduler;
import cn.slainc.modules.auth.closure.AccountClosureService;
import cn.slainc.modules.auth.closure.AccountClosureStore;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.upms.service.UserService;
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
    @ConditionalOnMissingBean(AuthSchemaInitializer.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "modality.auth.closure", name = "auto-create-table", matchIfMissing = true)
    public AuthSchemaInitializer authSchemaInitializer(DataSource dataSource) {
        return new AuthSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountClosureStore accountClosureStore(JdbcTemplate jdbcTemplate) {
        return new AccountClosureStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountClosureService accountClosureService(AccountClosureStore store, AuthProperties properties,
                                                       ObjectProvider<UserService> userService) {
        return new AccountClosureService(store, properties, userService);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public AccountClosureScheduler accountClosureScheduler(AccountClosureService closureService,
                                                           AuthProperties properties) {
        return new AccountClosureScheduler(closureService, properties);
    }
}
