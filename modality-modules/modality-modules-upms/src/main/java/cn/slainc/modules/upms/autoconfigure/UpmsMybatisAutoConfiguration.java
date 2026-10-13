package cn.slainc.modules.upms.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
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

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@AutoConfigureBefore(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.upms", name = "enabled", matchIfMissing = true)
public class UpmsMybatisAutoConfiguration {

    private static final List<String> GLOBAL_TABLES = List.of(UpmsKeys.PERMISSION_TABLE, UpmsKeys.MENU_TABLE,
            UpmsKeys.SYSTEM_REGISTRY_TABLE, UpmsKeys.OPERATION_LOG_TABLE, UpmsKeys.LOGIN_RECORD_TABLE);

    @Bean
    @ConditionalOnMissingBean(UpmsTableWhitelist.class)
    public UpmsTableWhitelist upmsTableWhitelist(ObjectProvider<MybatisProperties> mybatisProperties,
                                                 ObjectProvider<DefaultTenantLineHandler> tenantLineHandler) {
        return new UpmsTableWhitelist(GLOBAL_TABLES, mybatisProperties.getIfAvailable(),
                tenantLineHandler.getIfAvailable());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({SqlSessionFactory.class, org.mybatis.spring.mapper.MapperFactoryBean.class})
    @ConditionalOnBean(SqlSessionFactory.class)
    @MapperScan("cn.slainc.modules.upms.mapper")
    static class UpmsMapperConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "upmsSchemaInitializer")
        @ConditionalOnBean(DataSource.class)
        @ConditionalOnProperty(prefix = "modality.upms", name = "auto-create-table", matchIfMissing = true)
        public ClasspathSchemaInitializer upmsSchemaInitializer(DataSource dataSource) {
            return new ClasspathSchemaInitializer(dataSource, "upms-schema.sql");
        }
    }
}
