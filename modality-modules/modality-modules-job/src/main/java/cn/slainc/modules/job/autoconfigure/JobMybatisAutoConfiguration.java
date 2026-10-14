package cn.slainc.modules.job.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.modules.job.JobKeys;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
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

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@AutoConfigureBefore(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.job", name = "enabled", matchIfMissing = true)
public class JobMybatisAutoConfiguration {

    private static final List<String> GLOBAL_TABLES = List.of(JobKeys.TASK_TABLE, JobKeys.LOG_TABLE);

    @Bean
    @ConditionalOnMissingBean(JobTableWhitelist.class)
    public JobTableWhitelist jobTableWhitelist(ObjectProvider<MybatisProperties> mybatisProperties,
                                               ObjectProvider<DefaultTenantLineHandler> tenantLineHandler) {
        return new JobTableWhitelist(GLOBAL_TABLES, mybatisProperties.getIfAvailable(),
                tenantLineHandler.getIfAvailable());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({SqlSessionFactory.class, MapperFactoryBean.class})
    @ConditionalOnBean(SqlSessionFactory.class)
    @MapperScan("cn.slainc.modules.job.mapper")
    static class JobMapperConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "jobSchemaInitializer")
        @ConditionalOnBean(DataSource.class)
        @ConditionalOnProperty(prefix = "modality.job", name = "auto-create-table", matchIfMissing = true)
        public ClasspathSchemaInitializer jobSchemaInitializer(DataSource dataSource) {
            return new ClasspathSchemaInitializer(dataSource, "job-schema.sql");
        }
    }
}
