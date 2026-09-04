package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.monitor.satoken.ReflectiveSaTokenMetricsSource;
import cn.slainc.monitor.satoken.SaTokenMetrics;
import cn.slainc.monitor.satoken.SaTokenMetricsBinder;
import cn.slainc.monitor.satoken.SaTokenMetricsSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = MonitorAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "cn.dev33.satoken.SaManager")
@ConditionalOnProperty(prefix = "modality.monitor.satoken", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SaTokenMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SaTokenMetricsSource saTokenMetricsSource(MonitorProperties properties) {
        MonitorProperties.SaToken saToken = properties.getSaToken();
        return new ReflectiveSaTokenMetricsSource(ReflectiveSaTokenMetricsSource.MANAGER_CLASS_NAME,
                saToken.getTokenPrefix(), saToken.getSessionPrefix(), saToken.getScanLimit(), saToken.getCacheMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public SaTokenMetricsBinder saTokenMetricsBinder(SaTokenMetricsSource source, MetricsTemplate metricsTemplate,
                                                     MonitorProperties properties) {
        return new SaTokenMetricsBinder(source, metricsTemplate, properties.getSaToken().getInstance());
    }

    @Bean
    @ConditionalOnMissingBean
    public SaTokenMetrics saTokenMetrics(MetricsTemplate metricsTemplate) {
        return new SaTokenMetrics(metricsTemplate);
    }
}
