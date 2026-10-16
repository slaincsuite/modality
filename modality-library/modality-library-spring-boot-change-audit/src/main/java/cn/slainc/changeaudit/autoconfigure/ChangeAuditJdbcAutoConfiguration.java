package cn.slainc.changeaudit.autoconfigure;

import cn.slainc.changeaudit.dao.JdbcChangeRecorder;
import cn.slainc.changeaudit.spi.ChangeRecorder;
import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@AutoConfigureBefore(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = ChangeAuditProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class ChangeAuditJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChangeAuditTableWhitelist.class)
    public ChangeAuditTableWhitelist changeAuditTableWhitelist(ChangeAuditProperties properties,
                                                              ObjectProvider<MybatisProperties> mybatisProperties,
                                                              ObjectProvider<DefaultTenantLineHandler> tenantLineHandler) {
        return new ChangeAuditTableWhitelist(List.of(properties.getJdbc().getTableName()),
                mybatisProperties.getIfAvailable(), tenantLineHandler.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(ChangeAuditStoreValidator.class)
    public ChangeAuditStoreValidator changeAuditStoreValidator(ChangeAuditProperties properties) {
        return new ChangeAuditStoreValidator(properties.getStore());
    }

    @Bean
    @ConditionalOnMissingBean(ChangeRecorder.class)
    @ConditionalOnProperty(prefix = ChangeAuditProperties.PREFIX, name = "store", havingValue = "jdbc",
            matchIfMissing = true)
    public JdbcChangeRecorder changeAuditJdbcRecorder(JdbcTemplate jdbcTemplate, ChangeAuditProperties properties) {
        return new JdbcChangeRecorder(jdbcTemplate, properties.getJdbc().getTableName());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource.class)
    static class SchemaInitialization {

        @Bean
        @ConditionalOnMissingBean(name = "changeAuditSchemaInitializer")
        @ConditionalOnProperty(prefix = ChangeAuditProperties.PREFIX, name = "jdbc.auto-create-table",
                matchIfMissing = true)
        public ClasspathSchemaInitializer changeAuditSchemaInitializer(DataSource dataSource) {
            return new ClasspathSchemaInitializer(dataSource, "change-audit-schema.sql");
        }
    }
}
