package cn.slainc.tenant.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MicrometerTenantMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    private MicrometerTenantMetrics metrics;

    @BeforeEach
    void setUp() {
        registry.clear();
        metrics = new MicrometerTenantMetrics(registry);
    }

    @Test
    @DisplayName("解析成功在 modality.tenant.resolve 上记 resolved 标签")
    void recordsResolvedCounterPerTenant() {
        metrics.tenantResolved(7L);
        metrics.tenantResolved(7L);
        metrics.tenantResolved(9L);

        Counter counter = registry.find("modality.tenant.resolve").tag("result", "resolved").counter();
        assertNotNull(counter, "应存在 modality.tenant.resolve{result=resolved} 计数器");
        assertEquals(3.0D, counter.count());
        assertEquals(0.0D, registry.find("modality.tenant.resolve").tag("result", "missing").counter().count());
    }

    @Test
    @DisplayName("解析缺口在 modality.tenant.resolve 上记 missing 标签")
    void recordsMissingCounter() {
        metrics.tenantMissing();
        metrics.tenantMissing();

        Counter counter = registry.find("modality.tenant.resolve").tag("result", "missing").counter();
        assertNotNull(counter);
        assertEquals(2.0D, counter.count());
        assertEquals(0.0D, registry.find("modality.tenant.resolve").tag("result", "resolved").counter().count());
    }

    @Test
    @DisplayName("租户被拒绝在 modality.tenant.resolve 上记 rejected 标签")
    void recordsRejectedCounter() {
        metrics.tenantRejected(7L, 403000);
        metrics.tenantRejected(7L, 403000);

        Counter counter = registry.find("modality.tenant.resolve").tag("result", "rejected").counter();
        assertNotNull(counter);
        assertEquals(2.0D, counter.count());
    }

    @Test
    @DisplayName("配额超限使用独立指标名 modality.tenant.quota.exceeded")
    void recordsQuotaExceededCounter() {
        metrics.quotaExceeded(7L, "USERS");
        metrics.quotaExceeded(8L, "STORAGE");

        Counter counter = registry.find("modality.tenant.quota.exceeded").counter();
        assertNotNull(counter);
        assertEquals(2.0D, counter.count());
        assertEquals(1, registry.find("modality.tenant.quota.exceeded").counters().size());
    }

    @Test
    @DisplayName("指标名与标签稳定，重复注册不会产生重复计量器")
    void meterNamesAndTagsAreStable() {
        metrics.tenantResolved(1L);
        metrics.tenantMissing();
        metrics.tenantRejected(1L, 403000);
        new MicrometerTenantMetrics(registry).tenantResolved(1L);

        assertEquals(1, registry.find("modality.tenant.resolve").tag("result", "resolved").counters().size());
        assertEquals(1, registry.find("modality.tenant.resolve").tag("result", "missing").counters().size());
        assertEquals(1, registry.find("modality.tenant.resolve").tag("result", "rejected").counters().size());
        assertEquals(2.0D, registry.find("modality.tenant.resolve").tag("result", "resolved").counter().count());
        assertNull(registry.find("modality.tenant.resolve").tag("result", "unknown").counter());
        assertSame(registry.find("modality.tenant.resolve").tag("result", "resolved").counter(),
                registry.find("modality.tenant.resolve").tag("result", "resolved").counter());
    }

    @Test
    @DisplayName("构造即注册四个计量器，空实现不新增计量器")
    void noopRecorderDoesNotRegisterMeters() {
        assertEquals(4, registry.getMeters().size(), "构造时应注册 3 个 resolve 计数与 1 个配额计数");

        TenantMetricsRecorder.NOOP.tenantResolved(1L);
        TenantMetricsRecorder.NOOP.tenantMissing();
        TenantMetricsRecorder.NOOP.tenantRejected(1L, 403000);
        TenantMetricsRecorder.NOOP.quotaExceeded(1L, "USERS");

        assertEquals(4, registry.getMeters().size(), "空实现不得注册任何计量器");
        assertEquals(0.0D, registry.find("modality.tenant.resolve").tag("result", "resolved").counter().count());
        assertEquals(0.0D, registry.find("modality.tenant.quota.exceeded").counter().count());
    }
}
