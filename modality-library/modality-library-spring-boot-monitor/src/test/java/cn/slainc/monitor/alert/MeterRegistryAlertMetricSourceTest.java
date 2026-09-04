package cn.slainc.monitor.alert;

import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.monitor.metrics.MetricsSampler;
import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.monitor.support.MonitorTestSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MeterRegistryAlertMetricSourceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final MeterRegistryAlertMetricSource source = new MeterRegistryAlertMetricSource(registry);

    @Test
    @DisplayName("HTTP 错误率按 status 标签聚合，可区分 4xx 与 5xx")
    void shouldComputeHttpErrorRate() {
        record("200", 8);
        record("404", 1);
        record("500", 1);
        assertThat(source.value(MeterRegistryAlertMetricSource.HTTP_ERROR_RATE)).isEqualTo(0.2d);
        assertThat(source.value(MeterRegistryAlertMetricSource.HTTP_ERROR_RATE_4XX)).isEqualTo(0.1d);
        assertThat(source.value(MeterRegistryAlertMetricSource.HTTP_ERROR_RATE_5XX)).isEqualTo(0.1d);
    }

    @Test
    @DisplayName("堆使用率由 jvm.memory.used 与 jvm.memory.max 求和得到")
    void shouldComputeHeapUsage() {
        registry.gauge("jvm.memory.used", io.micrometer.core.instrument.Tags.of("area", "heap", "id", "G1 Eden"),
                1024d);
        registry.gauge("jvm.memory.max", io.micrometer.core.instrument.Tags.of("area", "heap", "id", "G1 Eden"),
                2048d);
        registry.gauge("jvm.memory.used", io.micrometer.core.instrument.Tags.of("area", "nonheap", "id", "Metaspace"),
                999d);
        assertThat(source.value(MeterRegistryAlertMetricSource.JVM_HEAP_USAGE)).isEqualTo(0.5d);
    }

    @Test
    @DisplayName("磁盘使用率取所有分区最大值")
    void shouldComputeMaxDiskUsage() {
        registry.gauge("modality.system.disk.usage", io.micrometer.core.instrument.Tags.of("mount", "/"), 0.4d);
        registry.gauge("modality.system.disk.usage", io.micrometer.core.instrument.Tags.of("mount", "/data"), 0.85d);
        assertThat(source.value(MeterRegistryAlertMetricSource.SYSTEM_DISK_USAGE)).isEqualTo(0.85d);
    }

    @Test
    @DisplayName("连接池指标复用 Boot 自带的 hikaricp_*，缺失时回退到 modality.db.pool.*")
    void shouldReuseHikariPoolMetrics() {
        registry.gauge("hikaricp_connections_pending", io.micrometer.core.instrument.Tags.of("pool", "master"), 3d);
        registry.gauge("hikaricp_connections_active", io.micrometer.core.instrument.Tags.of("pool", "master"), 4d);
        registry.gauge("hikaricp_connections_max", io.micrometer.core.instrument.Tags.of("pool", "master"), 8d);
        assertThat(source.value(MeterRegistryAlertMetricSource.DB_POOL_PENDING)).isEqualTo(3d);
        assertThat(source.value(MeterRegistryAlertMetricSource.DB_POOL_USAGE)).isEqualTo(0.5d);

        SimpleMeterRegistry fallback = new SimpleMeterRegistry();
        fallback.gauge("modality.db.pool.pending", io.micrometer.core.instrument.Tags.of("pool", "druid-pool",
                "type", "druid"), 2d);
        fallback.gauge("modality.db.pool.active", io.micrometer.core.instrument.Tags.of("pool", "druid-pool",
                "type", "druid"), 3d);
        fallback.gauge("modality.db.pool.max", io.micrometer.core.instrument.Tags.of("pool", "druid-pool",
                "type", "druid"), 6d);
        MeterRegistryAlertMetricSource druidSource = new MeterRegistryAlertMetricSource(fallback);
        assertThat(druidSource.value(MeterRegistryAlertMetricSource.DB_POOL_PENDING)).isEqualTo(2d);
        assertThat(druidSource.value(MeterRegistryAlertMetricSource.DB_POOL_USAGE)).isEqualTo(0.5d);
    }

    @Test
    @DisplayName("计数器用 rate(...) 求每秒速率，第一次没有基线返回 null")
    void shouldComputeCounterRate() {
        Counter counter = registry.counter("modality.business.order");
        counter.increment(10d);
        assertThat(source.value("rate(modality.business.order)")).isNull();
        counter.increment(10d);
        try {
            Thread.sleep(20L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        Double rate = source.value("rate(modality.business.order)");
        assertThat(rate).isNotNull().isGreaterThan(0d);
    }

    @Test
    @DisplayName("普通 Gauge 与 Counter 可直接按名取值")
    void shouldReadPlainMeters() {
        registry.gauge("modality.system.cpu.usage", 0.42d);
        registry.counter("modality.demo.total").increment(7d);
        assertThat(source.value("modality.system.cpu.usage")).isEqualTo(0.42d);
        assertThat(source.value("modality.demo.total")).isEqualTo(7d);
        assertThat(source.value("not-exists")).isNull();
        assertThat(source.value(null)).isNull();
    }

    @Test
    @DisplayName("业务 API 错误率与指标名集合")
    void shouldExposeDerivedAndMeterNames() {
        MetricsTemplate template = MonitorTestSupport.metricsTemplate(registry);
        BusinessMetrics businessMetrics = new BusinessMetrics(template,
                new MetricsSampler(MonitorTestSupport.dynamicConfig(MonitorTestSupport.dynamicConfigService(Map.of())),
                        1d));
        businessMetrics.apiCall(true);
        businessMetrics.apiCall(false);
        businessMetrics.apiCall(false);
        assertThat(source.value(MeterRegistryAlertMetricSource.BUSINESS_API_ERROR_RATE))
                .isCloseTo(2d / 3d, org.assertj.core.data.Offset.offset(1.0E-9));
        assertThat(source.metrics()).contains(MeterRegistryAlertMetricSource.HTTP_ERROR_RATE, "modality.business.api.call");
    }

    private void record(String status, int count) {
        Timer timer = Timer.builder("http.server.requests").tag("status", status).tag("uri", "/demo")
                .register(registry);
        for (int index = 0; index < count; index++) {
            timer.record(java.time.Duration.ofMillis(5));
        }
    }
}
