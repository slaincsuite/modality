package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.monitor.system.OshiSystemMetricsBinder;
import cn.slainc.monitor.system.OshiSystemMetricsCollector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import oshi.SystemInfo;

@AutoConfiguration(after = MonitorAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(SystemInfo.class)
@ConditionalOnProperty(prefix = "modality.monitor.system", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class OshiSystemMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OshiSystemMetricsCollector oshiSystemMetricsCollector(MonitorProperties properties) {
        MonitorProperties.SystemMetrics system = properties.getSystem();
        return new OshiSystemMetricsCollector(system.getCacheMillis(), system.getDiskMounts(),
                system.getNetworkInterfaces(), system.isPerCoreCpu());
    }

    @Bean
    @ConditionalOnMissingBean
    public OshiSystemMetricsBinder oshiSystemMetricsBinder(OshiSystemMetricsCollector collector,
                                                           MetricsTemplate metricsTemplate,
                                                           MonitorProperties properties) {
        return new OshiSystemMetricsBinder(collector, metricsTemplate, properties.getSystem().isPerCoreCpu());
    }
}
