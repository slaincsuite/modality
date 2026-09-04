package cn.slainc.monitor.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.monitor.config.MonitorDynamicConfig;
import cn.slainc.monitor.db.DatabaseMetrics;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.monitor.metrics.HttpConcurrencyMetrics;
import cn.slainc.monitor.metrics.MetricNaming;
import cn.slainc.monitor.metrics.MetricNamingMeterFilter;
import cn.slainc.monitor.metrics.MetricsSampler;
import cn.slainc.monitor.metrics.MetricsTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration(afterName = "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration")
@ConditionalOnClass({MeterRegistry.class, MetricsTemplate.class})
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public static MonitorDefaultsPostProcessor monitorDefaultsPostProcessor(Environment environment) {
        return new MonitorDefaultsPostProcessor(environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public MonitorDynamicConfig monitorDynamicConfig(ObjectProvider<DynamicConfigService> dynamicConfigService,
                                                     Environment environment, MonitorProperties properties) {
        return new MonitorDynamicConfig(dynamicConfigService, environment, properties.getConfigCacheMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricNaming metricNaming(MonitorProperties properties) {
        return new MetricNaming(properties.getMetrics().getPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricNamingMeterFilter metricNamingMeterFilter(MetricNaming naming) {
        return new MetricNamingMeterFilter(naming);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricsTemplate metricsTemplate(MeterRegistry registry, MetricNaming naming, MonitorProperties properties) {
        return new MetricsTemplate(registry, naming, properties.getMetrics().isHistogram());
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricsSampler metricsSampler(MonitorDynamicConfig config, MonitorProperties properties) {
        return new MetricsSampler(config, properties.getMetrics().getSamplingRate());
    }

    @Bean
    @ConditionalOnMissingBean
    public BusinessMetrics businessMetrics(MetricsTemplate metricsTemplate, MetricsSampler metricsSampler) {
        return new BusinessMetrics(metricsTemplate, metricsSampler);
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpConcurrencyMetrics httpConcurrencyMetrics(MetricsTemplate metricsTemplate) {
        return new HttpConcurrencyMetrics(metricsTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public DatabaseMetrics databaseMetrics(MetricsTemplate metricsTemplate) {
        return new DatabaseMetrics(metricsTemplate);
    }
}
