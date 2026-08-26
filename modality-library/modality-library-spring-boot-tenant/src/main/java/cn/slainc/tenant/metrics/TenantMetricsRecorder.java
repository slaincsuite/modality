package cn.slainc.tenant.metrics;

public interface TenantMetricsRecorder {

    TenantMetricsRecorder NOOP = new TenantMetricsRecorder() {
    };

    default void tenantResolved(Long tenantId) {
    }

    default void tenantMissing() {
    }

    default void tenantRejected(Long tenantId, int code) {
    }

    default void quotaExceeded(Long tenantId, String dimension) {
    }
}
