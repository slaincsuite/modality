package cn.slainc.modules.open.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.store.ConsentQueryDao;
import cn.slainc.modules.open.store.JdbcAppLogStore;
import cn.slainc.modules.open.store.JdbcAppStore;
import cn.slainc.modules.open.store.JdbcConsentStore;
import cn.slainc.modules.open.store.JdbcCredentialStore;
import cn.slainc.modules.open.store.JdbcScopeGroupStore;
import cn.slainc.modules.open.store.JdbcScopeStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

// JDBC 存储必须先于 open-api 库装配：四个 Store SPI 落库实现顶掉内存兜底，宿主无 JdbcTemplate 时退回内存实现
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        beforeName = "cn.slainc.openapi.autoconfigure.OpenApiAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "modality.open", name = "enabled", matchIfMissing = true)
public class OpenStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "openSchemaInitializer")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "modality.open", name = "auto-create-table", matchIfMissing = true)
    public ClasspathSchemaInitializer openSchemaInitializer(DataSource dataSource) {
        return new ClasspathSchemaInitializer(dataSource, "open-schema.sql");
    }

    @Bean
    @ConditionalOnMissingBean(cn.slainc.openapi.spi.AppStore.class)
    public JdbcAppStore openAppStore(JdbcTemplate jdbcTemplate) {
        return new JdbcAppStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(cn.slainc.openapi.spi.ScopeStore.class)
    public JdbcScopeStore openScopeStore(JdbcTemplate jdbcTemplate) {
        return new JdbcScopeStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcScopeGroupStore openScopeGroupStore(JdbcTemplate jdbcTemplate) {
        return new JdbcScopeGroupStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(cn.slainc.openapi.spi.ConsentStore.class)
    public JdbcConsentStore openConsentStore(JdbcTemplate jdbcTemplate) {
        return new JdbcConsentStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(cn.slainc.openapi.spi.CredentialStore.class)
    public JdbcCredentialStore openCredentialStore(JdbcTemplate jdbcTemplate) {
        return new JdbcCredentialStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcAppLogStore openAppLogStore(JdbcTemplate jdbcTemplate) {
        return new JdbcAppLogStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsentQueryDao openConsentQueryDao(JdbcTemplate jdbcTemplate) {
        return new ConsentQueryDao(jdbcTemplate);
    }
}
