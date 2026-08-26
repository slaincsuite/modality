package cn.slainc.tenant.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerTenantMetrics implements TenantMetricsRecorder {

    private final Counter resolved;

    private final Counter missing;

    private final Counter rejected;

    private final Counter quotaExceeded;

    public MicrometerTenantMetrics(MeterRegistry registry) {
        this.resolved = Counter.builder("modality.tenant.resolve").tag("result", "resolved").register(registry);
        this.missing = Counter.builder("modality.tenant.resolve").tag("result", "missing").register(registry);
        this.rejected = Counter.builder("modality.tenant.resolve").tag("result", "rejected").register(registry);
        this.quotaExceeded = Counter.builder("modality.tenant.quota.exceeded").register(registry);
    }

    @Override
    public void tenantResolved(Long tenantId) {
        resolved.increment();
    }

    @Override
    public void tenantMissing() {
        missing.increment();
    }

    @Override
    public void tenantRejected(Long tenantId, int code) {
        rejected.increment();
    }

    @Override
    public void quotaExceeded(Long tenantId, String dimension) {
        quotaExceeded.increment();
    }
}
