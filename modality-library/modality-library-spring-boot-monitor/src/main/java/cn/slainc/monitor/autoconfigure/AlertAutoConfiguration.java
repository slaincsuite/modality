package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.alert.AlertDispatcher;
import cn.slainc.monitor.alert.AlertEvaluationScheduler;
import cn.slainc.monitor.alert.AlertEvaluator;
import cn.slainc.monitor.alert.AlertMetricSource;
import cn.slainc.monitor.alert.AlertNotifier;
import cn.slainc.monitor.alert.AlertNotifierSource;
import cn.slainc.monitor.alert.AlertRuleProvider;
import cn.slainc.monitor.alert.AlertService;
import cn.slainc.monitor.alert.AlertSilenceRegistry;
import cn.slainc.monitor.alert.AlertSuppressor;
import cn.slainc.monitor.alert.CompositeAlertNotifierSource;
import cn.slainc.monitor.alert.HttpWebhookTransport;
import cn.slainc.monitor.alert.LoggingAlertNotifier;
import cn.slainc.monitor.alert.MeterRegistryAlertMetricSource;
import cn.slainc.monitor.alert.StaticAlertNotifierSource;
import cn.slainc.monitor.alert.WebhookNotifierSource;
import cn.slainc.monitor.alert.WebhookTransport;
import cn.slainc.monitor.config.MonitorDynamicConfig;
import cn.slainc.monitor.metrics.MetricsTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration(after = MonitorAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "modality.monitor.alert", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class AlertAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(java.time.Clock.class)
    public java.time.Clock alertClock() {
        return java.time.Clock.systemDefaultZone();
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertMetricSource alertMetricSource(MeterRegistry registry) {
        return new MeterRegistryAlertMetricSource(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertRuleProvider alertRuleProvider(MonitorDynamicConfig config, MonitorProperties properties) {
        return new AlertRuleProvider(config, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertEvaluator alertEvaluator() {
        return new AlertEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertSuppressor alertSuppressor() {
        return new AlertSuppressor();
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertSilenceRegistry alertSilenceRegistry(MonitorDynamicConfig config, MonitorProperties properties) {
        return new AlertSilenceRegistry(config, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookTransport webhookTransport(MonitorProperties properties) {
        return new HttpWebhookTransport(properties.getAlert().getConnectTimeout(),
                properties.getAlert().getReadTimeout(), properties.getAlert().isAllowInternalWebhooks());
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookNotifierSource webhookNotifierSource(MonitorDynamicConfig config, MonitorProperties properties,
                                                       WebhookTransport webhookTransport) {
        return new WebhookNotifierSource(config, properties, webhookTransport,
                properties.getAlert().getWebhookCacheMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertNotifierSource alertNotifierSource(MonitorProperties properties,
                                                   WebhookNotifierSource webhookNotifierSource,
                                                   ObjectProvider<LoggingAlertNotifier> loggingNotifier,
                                                   ObjectProvider<AlertNotifier> customNotifiers) {
        List<AlertNotifierSource> sources = new ArrayList<>();
        List<AlertNotifier> fixed = new ArrayList<>();
        LoggingAlertNotifier logging = loggingNotifier.getIfAvailable();
        if (logging != null) {
            fixed.add(logging);
        }
        customNotifiers.orderedStream()
                .filter(notifier -> !(notifier instanceof LoggingAlertNotifier))
                .forEach(fixed::add);
        if (!fixed.isEmpty()) {
            sources.add(new StaticAlertNotifierSource(fixed));
        }
        if (properties.getAlert().getNotifiers().isWebhook()) {
            sources.add(webhookNotifierSource);
        }
        return new CompositeAlertNotifierSource(sources);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertDispatcher alertDispatcher(AlertNotifierSource notifierSource,
                                           ObjectProvider<MetricsTemplate> metricsTemplate) {
        return new AlertDispatcher(notifierSource, metricsTemplate.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertService alertService(AlertRuleProvider ruleProvider, AlertMetricSource metricSource,
                                     AlertEvaluator evaluator, AlertSuppressor suppressor,
                                     AlertSilenceRegistry silenceRegistry, AlertDispatcher dispatcher,
                                     MonitorProperties properties,
                                     ObjectProvider<MetricsTemplate> metricsTemplate,
                                     ObjectProvider<java.time.Clock> clock) {
        return new AlertService(ruleProvider, metricSource, evaluator, suppressor, silenceRegistry, dispatcher,
                properties, metricsTemplate.getIfAvailable(), clock.getIfAvailable(java.time.Clock::systemDefaultZone));
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertEvaluationScheduler alertEvaluationScheduler(AlertService alertService, MonitorDynamicConfig config,
                                                             MonitorProperties properties) {
        return new AlertEvaluationScheduler(alertService, config, properties);
    }

    @Bean
    @ConditionalOnMissingBean(LoggingAlertNotifier.class)
    @ConditionalOnProperty(prefix = "modality.monitor.alert.notifiers", name = "log", havingValue = "true",
            matchIfMissing = true)
    public LoggingAlertNotifier loggingAlertNotifier() {
        return new LoggingAlertNotifier();
    }
}
