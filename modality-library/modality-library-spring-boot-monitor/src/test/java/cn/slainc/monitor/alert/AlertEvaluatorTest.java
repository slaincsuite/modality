package cn.slainc.monitor.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEvaluatorTest {

    private static final String METRIC = "demo.cpu.usage";

    private final AlertEvaluator evaluator = new AlertEvaluator();

    @Test
    @DisplayName("瞬时越界不触发告警")
    void shouldNotTriggerOnTransientBreach() {
        AlertRule rule = rule(10L);
        AlertMetricSource source = source(0.95d);
        assertThat(evaluator.evaluate(List.of(rule), source, 1_000L)).isEmpty();
        assertThat(evaluator.evaluate(List.of(rule), source, 5_000L)).isEmpty();
        AlertMetricSource recovered = source(0.10d);
        assertThat(evaluator.evaluate(List.of(rule), recovered, 9_000L)).isEmpty();
        assertThat(evaluator.isFiring(rule.getId())).isFalse();
    }

    @Test
    @DisplayName("持续越界达到持续时间后触发一次")
    void shouldTriggerAfterDuration() {
        AlertRule rule = rule(10L);
        AlertMetricSource source = source(0.95d);
        assertThat(evaluator.evaluate(List.of(rule), source, 1_000L)).isEmpty();
        assertThat(evaluator.evaluate(List.of(rule), source, 10_999L)).isEmpty();
        List<AlertEvent> events = evaluator.evaluate(List.of(rule), source, 11_000L);
        assertThat(events).hasSize(1);
        AlertEvent event = events.get(0);
        assertThat(event.type()).isEqualTo(AlertType.TRIGGER);
        assertThat(event.level()).isEqualTo(AlertLevel.WARN);
        assertThat(event.ruleId()).isEqualTo("demo-cpu");
        assertThat(event.value()).isEqualTo(0.95d);
        assertThat(event.threshold()).isEqualTo(0.9d);
        assertThat(event.message()).contains("告警触发").contains("demo-cpu");
        assertThat(evaluator.isFiring("demo-cpu")).isTrue();
        assertThat(evaluator.evaluate(List.of(rule), source, 20_000L)).isEmpty();
    }

    @Test
    @DisplayName("持续时间为 0 时首次越界即触发")
    void shouldTriggerImmediatelyWhenDurationIsZero() {
        List<AlertEvent> events = evaluator.evaluate(List.of(rule(0L)), source(1.0d), 1_000L);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(AlertType.TRIGGER);
    }

    @Test
    @DisplayName("恢复后发出恢复通知")
    void shouldRecover() {
        AlertRule rule = rule(0L);
        evaluator.evaluate(List.of(rule), source(1.0d), 1_000L);
        List<AlertEvent> events = evaluator.evaluate(List.of(rule), source(0.2d), 2_000L);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(AlertType.RECOVER);
        assertThat(events.get(0).message()).contains("告警恢复");
        assertThat(evaluator.isFiring("demo-cpu")).isFalse();
        assertThat(evaluator.evaluate(List.of(rule), source(0.2d), 3_000L)).isEmpty();
    }

    @Test
    @DisplayName("停用的规则不参与评估")
    void shouldSkipDisabledRule() {
        AlertRule rule = rule(0L);
        rule.setEnabled(false);
        assertThat(evaluator.evaluate(List.of(rule), source(1.0d), 1_000L)).isEmpty();
    }

    @Test
    @DisplayName("指标缺失时跳过评估并保留已有状态")
    void shouldSkipMissingMetric() {
        AlertRule rule = rule(0L);
        evaluator.evaluate(List.of(rule), source(1.0d), 1_000L);
        assertThat(evaluator.evaluate(List.of(rule), name -> null, 2_000L)).isEmpty();
        assertThat(evaluator.isFiring("demo-cpu")).isTrue();
        assertThat(evaluator.evaluate(List.of(rule), source(0.1d), 3_000L)).hasSize(1);
    }

    @Test
    @DisplayName("比较运算符覆盖大小与等值判断")
    void shouldSupportAllOperators() {
        assertThat(AlertOperator.GREATER_THAN.test(2d, 1d)).isTrue();
        assertThat(AlertOperator.GREATER_OR_EQUAL.test(1d, 1d)).isTrue();
        assertThat(AlertOperator.LESS_THAN.test(0.5d, 1d)).isTrue();
        assertThat(AlertOperator.LESS_OR_EQUAL.test(1d, 1d)).isTrue();
        assertThat(AlertOperator.EQUAL.test(1d, 1d)).isTrue();
        assertThat(AlertOperator.NOT_EQUAL.test(1.5d, 1d)).isTrue();
        assertThat(AlertOperator.parse(">", AlertOperator.LESS_THAN)).isEqualTo(AlertOperator.GREATER_THAN);
        assertThat(AlertOperator.parse("gte", AlertOperator.LESS_THAN)).isEqualTo(AlertOperator.GREATER_OR_EQUAL);
        assertThat(AlertOperator.parse("less_than", AlertOperator.GREATER_THAN)).isEqualTo(AlertOperator.LESS_THAN);
        assertThat(AlertOperator.parse("未知", AlertOperator.GREATER_THAN)).isEqualTo(AlertOperator.GREATER_THAN);
        assertThat(AlertLevel.parse("error", AlertLevel.WARN)).isEqualTo(AlertLevel.ERROR);
        assertThat(AlertLevel.parse(null, AlertLevel.WARN)).isEqualTo(AlertLevel.WARN);
    }

    @Test
    @DisplayName("规则集合变化后清理过期状态，快照可观测")
    void shouldPruneStatesAndExposeSnapshot() {
        AlertRule rule = rule(0L);
        evaluator.evaluate(List.of(rule), source(1.0d), 1_000L);
        assertThat(evaluator.firingStates()).containsEntry("demo-cpu", true);
        evaluator.evaluate(List.of(), source(1.0d), 2_000L);
        assertThat(evaluator.firingStates()).isEmpty();
        assertThat(evaluator.lastValue("demo-cpu")).isNull();
    }

    private static AlertRule rule(long durationSeconds) {
        AlertRule rule = AlertRule.of("demo-cpu", "CPU 使用率过高", METRIC, AlertOperator.GREATER_THAN, 0.9d,
                durationSeconds, AlertLevel.WARN);
        rule.setUnit("%");
        return rule;
    }

    private static AlertMetricSource source(double value) {
        return new AlertMetricSource() {
            @Override
            public Double value(String metric) {
                return METRIC.equals(metric) ? value : null;
            }

            @Override
            public java.util.Set<String> metrics() {
                return java.util.Set.of(METRIC);
            }
        };
    }

    @Test
    @DisplayName("组合指标源按顺序取第一个可用值")
    void shouldUseCompositeSource() {
        AlertMetricSource empty = metric -> null;
        AlertMetricSource fixed = new AlertMetricSource() {
            @Override
            public Double value(String metric) {
                return 1.5d;
            }

            @Override
            public java.util.Set<String> metrics() {
                return java.util.Set.of("any");
            }
        };
        CompositeAlertMetricSource composite = new CompositeAlertMetricSource(List.of(empty, fixed));
        assertThat(composite.value("any")).isEqualTo(1.5d);
        assertThat(composite.snapshot(java.util.Set.of("any"))).containsEntry("any", 1.5d);
        assertThat(composite.metrics()).containsExactly("any");
        assertThat(new CompositeAlertMetricSource(null).value("any")).isNull();
    }
}
