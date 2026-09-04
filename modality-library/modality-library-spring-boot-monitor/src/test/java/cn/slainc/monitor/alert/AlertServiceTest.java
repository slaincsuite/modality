package cn.slainc.monitor.alert;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.monitor.metrics.MetricsSampler;
import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.monitor.support.MonitorTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertServiceTest {

    private static final String METRIC = "business.api.error.rate";

    private SimpleMeterRegistry registry;

    private MonitorProperties properties;

    private AlertRuleProvider ruleProvider;

    private AlertEvaluator evaluator;

    private AlertSuppressor suppressor;

    private AlertSilenceRegistry silenceRegistry;

    private RecordingAlertNotifier notifier;

    private AlertService service;

    private double value = 0.95d;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        properties = new MonitorProperties();
        properties.getAlert().setSuppressSeconds(300L);
        String ruleJson = "[{\"id\":\"api-error-rate-high\",\"name\":\"API 错误率过高\","
                + "\"metric\":\"business.api.error.rate\",\"compare\":\">\",\"threshold\":0.5,"
                + "\"durationSeconds\":10,\"level\":\"WARN\"}]";
        ruleProvider = new AlertRuleProvider(MonitorTestSupport.dynamicConfig(MonitorTestSupport.dynamicConfigService(
                Map.of(AlertRuleProvider.RULES_KEY, ruleJson))), properties);
        evaluator = new AlertEvaluator();
        suppressor = new AlertSuppressor();
        silenceRegistry = new AlertSilenceRegistry(MonitorTestSupport.dynamicConfig(
                MonitorTestSupport.dynamicConfigService(Map.of())), properties);
        notifier = new RecordingAlertNotifier();
        MetricsTemplate template = MonitorTestSupport.metricsTemplate(registry);
        AlertDispatcher dispatcher = new AlertDispatcher(new StaticAlertNotifierSource(List.of(notifier)), template);
        service = new AlertService(ruleProvider, metric -> METRIC.equals(metric) ? value : null, evaluator, suppressor,
                silenceRegistry, dispatcher, properties, template,
                Clock.fixed(java.time.Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));
        assertThat(ruleProvider.rules()).hasSize(1);
    }

    @Test
    @DisplayName("持续越界够久后触发，并通过假 Notifier 断言收到了什么")
    void shouldTriggerAndNotify() {
        assertThat(service.evaluateOnce(1_000L)).isEmpty();
        assertThat(service.evaluateOnce(5_000L)).isEmpty();
        List<AlertEvent> notified = service.evaluateOnce(11_000L);
        assertThat(notified).hasSize(1);
        AlertEvent event = notifier.single();
        assertThat(event.type()).isEqualTo(AlertType.TRIGGER);
        assertThat(event.ruleId()).isEqualTo("api-error-rate-high");
        assertThat(event.value()).isEqualTo(0.95d);
        assertThat(event.threshold()).isEqualTo(0.5d);
        assertThat(event.message()).contains("告警触发").contains("持续=10秒");
        assertThat(registry.get("modality.alert.triggered").tag("rule", "api-error-rate-high")
                .tag("level", "WARN").counter().count()).isEqualTo(1d);
        assertThat(registry.get("modality.alert.notifications").tag("notifier", "recording")
                .tag("result", "success").counter().count()).isEqualTo(1d);
        assertThat(service.isFiring("api-error-rate-high")).isTrue();
    }

    @Test
    @DisplayName("瞬时越界不触发")
    void shouldNotNotifyOnTransientBreach() {
        assertThat(service.evaluateOnce(1_000L)).isEmpty();
        assertThat(service.evaluateOnce(5_000L)).isEmpty();
        value = 0.1d;
        assertThat(service.evaluateOnce(9_000L)).isEmpty();
        assertThat(notifier.events()).isEmpty();
    }

    @Test
    @DisplayName("恢复后发送恢复通知")
    void shouldNotifyRecovery() {
        service.evaluateOnce(1_000L);
        service.evaluateOnce(11_000L);
        assertThat(notifier.events()).hasSize(1);
        notifier.clear();
        value = 0.01d;
        List<AlertEvent> notified = service.evaluateOnce(12_000L);
        assertThat(notified).hasSize(1);
        assertThat(notifier.single().type()).isEqualTo(AlertType.RECOVER);
        assertThat(registry.get("modality.alert.recovered").tag("rule", "api-error-rate-high").counter().count())
                .isEqualTo(1d);
    }

    @Test
    @DisplayName("静默期内不发送任何通知，包括恢复通知")
    void shouldRespectSilence() {
        silenceRegistry.silenceAll(Duration.ofMinutes(5));
        service.evaluateOnce(1_000L);
        service.evaluateOnce(11_000L);
        assertThat(notifier.events()).isEmpty();
        assertThat(registry.get("modality.alert.silenced").tag("rule", "api-error-rate-high").counter().count())
                .isEqualTo(1d);
        value = 0.01d;
        service.evaluateOnce(12_000L);
        assertThat(notifier.events()).isEmpty();
        silenceRegistry.cancelAll();
        assertThat(silenceRegistry.anySilenced()).isFalse();
    }

    @Test
    @DisplayName("动态配置里的静默窗口同样生效")
    void shouldRespectConfigSilence() {
        MonitorProperties local = new MonitorProperties();
        AlertSilenceRegistry configSilence = new AlertSilenceRegistry(MonitorTestSupport.dynamicConfig(
                MonitorTestSupport.dynamicConfigService(Map.of(
                        AlertSilenceRegistry.SILENCE_UNTIL_KEY, String.valueOf(System.currentTimeMillis() + 60_000L),
                        AlertSilenceRegistry.SILENCE_RULES_KEY, "api-error-rate-high"))), local);
        assertThat(configSilence.isSilenced("api-error-rate-high", System.currentTimeMillis())).isTrue();
        assertThat(configSilence.isSilenced("other-rule", System.currentTimeMillis())).isFalse();
    }

    @Test
    @DisplayName("抑制窗口内同类告警合并，恢复通知不受抑制")
    void shouldSuppressDuplicatedAlerts() {
        service.evaluateOnce(1_000L);
        service.evaluateOnce(11_000L);
        assertThat(notifier.events()).hasSize(1);
        value = 0.01d;
        service.evaluateOnce(12_000L);
        assertThat(notifier.events()).hasSize(2);
        value = 0.95d;
        service.evaluateOnce(13_000L);
        service.evaluateOnce(23_000L);
        value = 0.01d;
        service.evaluateOnce(24_000L);
        value = 0.95d;
        service.evaluateOnce(25_000L);
        service.evaluateOnce(35_000L);
        assertThat(notifier.events()).extracting(AlertEvent::type)
                .containsExactly(AlertType.TRIGGER, AlertType.RECOVER, AlertType.RECOVER);
        assertThat(registry.get("modality.alert.suppressed").tag("rule", "api-error-rate-high").counter().count())
                .isEqualTo(2d);
        assertThat(registry.get("modality.alert.triggered").tag("rule", "api-error-rate-high")
                .tag("level", "WARN").counter().count()).isEqualTo(3d);
    }

    @Test
    @DisplayName("通知级别高于事件级别时不发送")
    void shouldFilterByNotifierLevel() {
        RecordingAlertNotifier errorOnly = new RecordingAlertNotifier("error-only", AlertLevel.ERROR);
        AlertDispatcher dispatcher = new AlertDispatcher(
                new StaticAlertNotifierSource(List.of(errorOnly, notifier)),
                MonitorTestSupport.metricsTemplate(registry));
        AlertService filtered = new AlertService(ruleProvider, metric -> METRIC.equals(metric) ? value : null,
                new AlertEvaluator(), new AlertSuppressor(), silenceRegistry, dispatcher, properties,
                MonitorTestSupport.metricsTemplate(registry),
                Clock.fixed(java.time.Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));
        filtered.evaluateOnce(1_000L);
        filtered.evaluateOnce(11_000L);
        assertThat(errorOnly.events()).isEmpty();
        assertThat(notifier.events()).hasSize(1);
    }

    @Test
    @DisplayName("未配置规则时评估不产生任何事件")
    void shouldDoNothingWithoutRules() {
        MonitorProperties bare = new MonitorProperties();
        bare.getAlert().setDefaultRules(false);
        AlertService empty = new AlertService(new AlertRuleProvider(MonitorTestSupport.dynamicConfig(
                MonitorTestSupport.dynamicConfigService(Map.of())), bare),
                metric -> 1d, new AlertEvaluator(), new AlertSuppressor(), silenceRegistry,
                new AlertDispatcher(new StaticAlertNotifierSource(List.of(notifier)),
                        MonitorTestSupport.metricsTemplate(registry)),
                bare, MonitorTestSupport.metricsTemplate(registry),
                Clock.fixed(java.time.Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));
        assertThat(empty.evaluateOnce(1_000L)).isEmpty();
        assertThat(empty.rules()).isEmpty();
    }

    @Test
    @DisplayName("抑制器窗口内返回真，窗口外放行")
    void shouldSuppressWithinWindow() {
        assertThat(suppressor.suppress("rule", 1_000L, 300L)).isFalse();
        assertThat(suppressor.suppress("rule", 1_200L, 300L)).isTrue();
        assertThat(suppressor.suppress("rule", 400_000L, 300L)).isFalse();
        suppressor.reset("rule");
        assertThat(suppressor.suppress("rule", 400_100L, 300L)).isFalse();
        suppressor.clear();
        assertThat(suppressor.suppress("rule", 400_200L, 0L)).isFalse();
    }

    @Test
    @DisplayName("业务指标门面可用于告警指标源")
    void shouldExposeBusinessErrorRate() {
        MetricsTemplate template = MonitorTestSupport.metricsTemplate(registry);
        BusinessMetrics businessMetrics = new BusinessMetrics(template,
                new MetricsSampler(MonitorTestSupport.dynamicConfig(MonitorTestSupport.dynamicConfigService(Map.of())),
                        1d));
        businessMetrics.apiCall(true);
        businessMetrics.apiCall(false);
        MeterRegistryAlertMetricSource source = new MeterRegistryAlertMetricSource(registry);
        assertThat(source.value(MeterRegistryAlertMetricSource.BUSINESS_API_ERROR_RATE)).isEqualTo(0.5d);
    }
}
