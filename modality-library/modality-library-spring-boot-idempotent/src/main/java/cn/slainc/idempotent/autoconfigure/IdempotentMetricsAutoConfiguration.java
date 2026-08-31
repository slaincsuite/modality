package cn.slainc.idempotent.autoconfigure;

import cn.slainc.idempotent.metrics.IdempotentMetrics;
import cn.slainc.idempotent.metrics.MicrometerIdempotentMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = IdempotentAutoConfiguration.class)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(prefix = IdempotentProperties.PREFIX + ".metrics", name = "enabled", matchIfMissing = true)
public class IdempotentMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentMetrics.class)
    public MicrometerIdempotentMetrics micrometerIdempotentMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        return new MicrometerIdempotentMetrics(meterRegistry.getIfAvailable());
    }
}
