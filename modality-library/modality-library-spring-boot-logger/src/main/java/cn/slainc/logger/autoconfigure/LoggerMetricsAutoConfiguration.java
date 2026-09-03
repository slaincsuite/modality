package cn.slainc.logger.autoconfigure;

import cn.slainc.logger.monitor.LogMetrics;
import cn.slainc.logger.monitor.MicrometerLogMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration"})
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "modality.logger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoggerMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LogMetrics.class)
    @ConditionalOnProperty(prefix = "modality.logger.monitor", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public MicrometerLogMetrics micrometerLogMetrics(MeterRegistry registry) {
        return new MicrometerLogMetrics(registry);
    }
}
