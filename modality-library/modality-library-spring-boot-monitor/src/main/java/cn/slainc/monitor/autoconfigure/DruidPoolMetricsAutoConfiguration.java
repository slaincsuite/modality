package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.db.DataSourcePoolIntrospector;
import cn.slainc.monitor.db.DruidPoolMetricsBinder;
import cn.slainc.monitor.metrics.MetricsTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(after = MonitorAutoConfiguration.class, afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration"})
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "com.alibaba.druid.pool.DruidDataSource")
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "modality.monitor.db", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DruidPoolMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataSourcePoolIntrospector dataSourcePoolIntrospector() {
        return new DataSourcePoolIntrospector();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.monitor.db", name = "druid", havingValue = "true",
            matchIfMissing = true)
    public DruidPoolMetricsBinder druidPoolMetricsBinder(ObjectProvider<DataSource> dataSources,
                                                         DataSourcePoolIntrospector introspector,
                                                         MetricsTemplate metricsTemplate,
                                                         MonitorProperties properties) {
        return new DruidPoolMetricsBinder(dataSources, introspector, metricsTemplate,
                properties.getDb().getRefreshMillis());
    }
}
