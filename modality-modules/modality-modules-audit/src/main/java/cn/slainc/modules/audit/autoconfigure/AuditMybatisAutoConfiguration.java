package cn.slainc.modules.audit.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.modules.audit.AuditKeys;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@AutoConfigureBefore(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.audit", name = "enabled", matchIfMissing = true)
public class AuditMybatisAutoConfiguration {

    private static final List<String> GLOBAL_TABLES = List.of(AuditKeys.AUDIT_LOG_TABLE, AuditKeys.API_LOG_TABLE,
            AuditKeys.POLICY_LOG_TABLE);

    @Bean
    @ConditionalOnMissingBean(AuditTableWhitelist.class)
    public AuditTableWhitelist auditTableWhitelist(ObjectProvider<MybatisProperties> mybatisProperties,
                                                   ObjectProvider<DefaultTenantLineHandler> tenantLineHandler) {
        return new AuditTableWhitelist(GLOBAL_TABLES, mybatisProperties.getIfAvailable(),
                tenantLineHandler.getIfAvailable());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({SqlSessionFactory.class, MapperFactoryBean.class})
    @ConditionalOnBean(SqlSessionFactory.class)
    @MapperScan("cn.slainc.modules.audit.mapper")
    static class AuditMapperConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "auditSchemaInitializer")
        @ConditionalOnBean(DataSource.class)
        @ConditionalOnProperty(prefix = "modality.audit", name = "auto-create-table", matchIfMissing = true)
        public ClasspathSchemaInitializer auditSchemaInitializer(DataSource dataSource) {
            return new ClasspathSchemaInitializer(dataSource, "audit-schema.sql");
        }
    }
}
