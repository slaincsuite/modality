package cn.slainc.modules.workorder.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
@ConditionalOnProperty(prefix = "modality.workorder", name = "enabled", matchIfMissing = true)
public class WorkorderMybatisAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({SqlSessionFactory.class, MapperFactoryBean.class})
    @ConditionalOnBean(SqlSessionFactory.class)
    @MapperScan("cn.slainc.modules.workorder.mapper")
    static class WorkorderMapperConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "workorderSchemaInitializer")
        @ConditionalOnBean(DataSource.class)
        @ConditionalOnProperty(prefix = "modality.workorder", name = "auto-create-table", matchIfMissing = true)
        public ClasspathSchemaInitializer workorderSchemaInitializer(DataSource dataSource) {
            return new ClasspathSchemaInitializer(dataSource, "workorder-schema.sql");
        }
    }
}
