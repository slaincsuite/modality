package cn.slainc.tenant.autoconfigure;

import cn.slainc.tenant.metrics.MicrometerTenantMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(prefix = "modality.tenant", name = "enabled", havingValue = "true")
public class TenantMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MicrometerTenantMetrics micrometerTenantMetrics(MeterRegistry meterRegistry) {
        return new MicrometerTenantMetrics(meterRegistry);
    }
}
