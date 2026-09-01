package cn.slainc.storage.autoconfigure;

import cn.slainc.storage.metrics.MicrometerStorageMetrics;
import cn.slainc.storage.metrics.StorageMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "modality.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StorageMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StorageMetricsRecorder storageMetricsRecorder(MeterRegistry meterRegistry) {
        return new MicrometerStorageMetrics(meterRegistry);
    }
}
