package cn.slainc.services.livemonitor.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
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

import javax.sql.DataSource;

@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@AutoConfigureBefore(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.live-monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiveMonitorMybatisAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({SqlSessionFactory.class, MapperFactoryBean.class})
    @ConditionalOnBean(SqlSessionFactory.class)
    @MapperScan("cn.slainc.services.livemonitor.mapper")
    static class LiveStreamMapperConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "liveMonitorSchemaInitializer")
        @ConditionalOnBean(DataSource.class)
        @ConditionalOnProperty(prefix = "modality.live-monitor", name = "auto-create-table", matchIfMissing = true)
        public ClasspathSchemaInitializer liveMonitorSchemaInitializer(DataSource dataSource) {
            return new ClasspathSchemaInitializer(dataSource, "live-monitor-schema.sql");
        }
    }
}
