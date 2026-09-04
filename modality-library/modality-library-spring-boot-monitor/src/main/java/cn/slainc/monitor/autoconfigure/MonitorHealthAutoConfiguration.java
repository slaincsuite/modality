package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.health.BusinessHealthCheck;
import cn.slainc.monitor.health.BusinessHealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = MonitorAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "modality.monitor.health", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MonitorHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "businessHealthIndicator")
    public HealthIndicator businessHealthIndicator(ObjectProvider<BusinessHealthCheck> checks) {
        return new BusinessHealthIndicator(checks.orderedStream().toList());
    }
}
