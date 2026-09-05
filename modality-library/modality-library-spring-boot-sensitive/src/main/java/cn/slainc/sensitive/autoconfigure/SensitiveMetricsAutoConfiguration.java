package cn.slainc.sensitive.autoconfigure;

import cn.slainc.sensitive.metrics.MicrometerSensitiveMetrics;
import cn.slainc.sensitive.metrics.SensitiveMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = SensitiveAutoConfiguration.class,
        afterName = "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration")
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class SensitiveMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SensitiveMetrics.class)
    @ConditionalOnBean(MeterRegistry.class)
    public SensitiveMetrics micrometerSensitiveMetrics(MeterRegistry registry) {
        return new MicrometerSensitiveMetrics(registry);
    }
}
