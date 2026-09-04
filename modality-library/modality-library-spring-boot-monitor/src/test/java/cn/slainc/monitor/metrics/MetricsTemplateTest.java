package cn.slainc.monitor.metrics;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsTemplateTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final MetricsTemplate template = new MetricsTemplate(registry, new MetricNaming(), true);

    @Test
    @DisplayName("计数器支持标签与增量累加")
    void shouldRecordCounter() {
        template.increment("demo.calls", "type", "login");
        template.increment("demo.calls", "type", "login");
        template.increment("demo.calls", 3d, "type", "order");
        assertThat(registry.get("modality.demo.calls").tag("type", "login").counter().count()).isEqualTo(2d);
        assertThat(template.counterValue("demo.calls", "type", "order")).isEqualTo(3d);
        assertThat(template.counterValue("demo.calls", "type", "missing")).isZero();
    }

    @Test
    @DisplayName("Gauge 支持数字状态对象与取值函数")
    void shouldRecordGauge() {
        AtomicInteger counter = new AtomicInteger(7);
        template.gauge("demo.queue.size", counter);
        template.gauge("demo.queue.busy", "队列繁忙度", counter, value -> value.doubleValue() / 10d);
        assertThat(template.gaugeValue("demo.queue.size")).isEqualTo(7d);
        assertThat(template.gaugeValue("demo.queue.busy")).isEqualTo(0.7d);
        assertThat(template.gaugeValue("demo.queue.missing")).isNaN();
    }

    @Test
    @DisplayName("Timer 支持直方图与耗时统计")
    void shouldRecordTimer() {
        Timer timer = template.timer("demo.request.duration", "uri", "/demo");
        timer.record(Duration.ofMillis(20));
        String result = template.record("demo.request.duration", () -> "ok", "uri", "/demo");
        assertThat(result).isEqualTo("ok");
        assertThat(timer.count()).isEqualTo(2L);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(20d);
        assertThat(timer.getId().getTag("uri")).isEqualTo("/demo");
    }

    @Test
    @DisplayName("Timer Sample 可用于跨方法计时")
    void shouldRecordSample() {
        Timer.Sample sample = template.startSample();
        long elapsed = template.stopSample(sample, "demo.sample");
        assertThat(elapsed).isGreaterThanOrEqualTo(0L);
        assertThat(template.registry().get("modality.demo.sample").timer().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("DistributionSummary 支持记录数值分布")
    void shouldRecordSummary() {
        template.record("demo.order.amount", 12.5d, "status", "paid");
        template.record("demo.order.amount", 7.5d, "status", "paid");
        assertThat(registry.get("modality.demo.order.amount").tag("status", "paid").summary().count()).isEqualTo(2L);
        assertThat(template.summary("demo.order.amount", "status", "paid").totalAmount()).isEqualTo(20d);
    }

    @Test
    @DisplayName("LongTaskTimer 能反映正在执行的长任务")
    void shouldRecordLongTask() {
        LongTaskTimer.Sample sample = template.startLongTask("demo.batch");
        assertThat(template.longTaskTimer("demo.batch").activeTasks()).isEqualTo(1);
        long elapsed = template.stopLongTask(sample);
        assertThat(elapsed).isGreaterThanOrEqualTo(0L);
        assertThat(template.longTaskTimer("demo.batch").activeTasks()).isZero();
    }

    @Test
    @DisplayName("未指定前缀的指标名会被规范化")
    void shouldNormalizeName() {
        template.increment("Demo_Custom_Metric");
        assertThat(registry.find("modality.demo.custom.metric").counter()).isNotNull();
        assertThat(template.name("Demo_Custom_Metric")).isEqualTo("modality.demo.custom.metric");
        assertThat(template.naming().prefix()).isEqualTo("modality");
    }

    @Test
    @DisplayName("构造时校验 MeterRegistry")
    void shouldRejectNullRegistry() {
        assertThatThrownBy(() -> new MetricsTemplate(null, new MetricNaming(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MeterRegistry 不能为空");
    }
}
