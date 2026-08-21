package cn.slainc.datasource.autoconfigure;

import cn.slainc.datasource.health.DataSourceHealthIndicator;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RoutingDataSourceAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(DynamicDataSourceManager.class)
@ConditionalOnProperty(prefix = "modality.datasource.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSourceHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "dataSourceHealthIndicator")
    public HealthIndicator dataSourceHealthIndicator(DynamicDataSourceManager manager) {
        return new DataSourceHealthIndicator(manager);
    }
}
