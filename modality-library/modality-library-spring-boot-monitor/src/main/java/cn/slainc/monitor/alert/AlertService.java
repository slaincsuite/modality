package cn.slainc.monitor.alert;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.metrics.MetricsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    public static final String EVALUATIONS = "modality.alert.evaluations";

    public static final String TRIGGERED = "modality.alert.triggered";

    public static final String RECOVERED = "modality.alert.recovered";

    public static final String SILENCED = "modality.alert.silenced";

    public static final String SUPPRESSED = "modality.alert.suppressed";

    private final AlertRuleProvider ruleProvider;

    private final AlertMetricSource metricSource;

    private final AlertEvaluator evaluator;

    private final AlertSuppressor suppressor;

    private final AlertSilenceRegistry silenceRegistry;

    private final AlertDispatcher dispatcher;

    private final MonitorProperties properties;

    private final MetricsTemplate metrics;

    private final Clock clock;

    public AlertService(AlertRuleProvider ruleProvider, AlertMetricSource metricSource, AlertEvaluator evaluator,
                        AlertSuppressor suppressor, AlertSilenceRegistry silenceRegistry, AlertDispatcher dispatcher,
                        MonitorProperties properties, MetricsTemplate metrics, Clock clock) {
        this.ruleProvider = ruleProvider;
        this.metricSource = metricSource;
        this.evaluator = evaluator;
        this.suppressor = suppressor;
        this.silenceRegistry = silenceRegistry;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public List<AlertEvent> evaluateOnce() {
        return evaluateOnce(clock.millis());
    }

    public List<AlertEvent> evaluateOnce(long nowMillis) {
        List<AlertRule> rules = ruleProvider.rules();
        if (metrics != null) {
            metrics.increment(EVALUATIONS);
        }
        List<AlertEvent> events = evaluator.evaluate(rules, metricSource, nowMillis);
        if (events.isEmpty()) {
            return List.of();
        }
        Map<String, AlertRule> rulesById = new HashMap<>();
        for (AlertRule rule : rules) {
            rulesById.put(rule.getId(), rule);
        }
        List<AlertEvent> notified = new ArrayList<>();
        for (AlertEvent event : events) {
            AlertRule rule = rulesById.get(event.ruleId());
            if (event.type() == AlertType.TRIGGER) {
                if (metrics != null) {
                    metrics.increment(TRIGGERED, "level", event.level().name(), "rule", event.ruleId());
                }
            } else if (metrics != null) {
                metrics.increment(RECOVERED, "rule", event.ruleId());
            }
            if (silenceRegistry.isSilenced(rule, nowMillis)) {
                if (metrics != null) {
                    metrics.increment(SILENCED, "rule", event.ruleId());
                }
                log.info("规则 {} 处于静默期，本次不发送通知", event.ruleId());
                continue;
            }
            long windowSeconds = rule == null
                    ? properties.getAlert().getSuppressSeconds()
                    : rule.resolvedSuppressSeconds(properties.getAlert().getSuppressSeconds());
            long window = windowSeconds * 1000L;
            if (event.type() == AlertType.TRIGGER && suppressor.suppress(event.ruleId(), nowMillis, window)) {
                if (metrics != null) {
                    metrics.increment(SUPPRESSED, "rule", event.ruleId());
                }
                log.info("规则 {} 在 {} 秒抑制窗口内，同类告警已合并", event.ruleId(), windowSeconds);
                continue;
            }
            int sent = dispatcher.dispatch(event);
            if (sent <= 0 && event.type() == AlertType.TRIGGER) {
                // 一个通道都没发出去就回滚抑制窗口，否则一次抖动会让整段抑制周期内的告警全部丢失
                suppressor.release(event.ruleId(), nowMillis);
                log.warn("规则 {} 的告警没有任何通道发送成功，已回滚抑制窗口等待下次重试", event.ruleId());
            }
            notified.add(event);
        }
        return notified;
    }

    public boolean isFiring(String ruleId) {
        return evaluator.isFiring(ruleId);
    }

    public List<AlertRule> rules() {
        return ruleProvider.rules();
    }

    public AlertEvaluator evaluator() {
        return evaluator;
    }
}
