package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.jvm.JvmSupplementMetricsBinder;
import io.micrometer.core.instrument.binder.jvm.JvmThreadDeadlockMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = MonitorAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(JvmThreadDeadlockMetrics.class)
@ConditionalOnProperty(prefix = "modality.monitor.jvm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JvmSupplementMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JvmSupplementMetricsBinder jvmSupplementMetricsBinder() {
        return new JvmSupplementMetricsBinder();
    }
}
