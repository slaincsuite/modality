package cn.slainc.tenant.lifecycle;

public record TenantLifecycleOptions(int trialDays, int defaultExpireDays, boolean initializeOnCreate,
                                     boolean removeIsolationOnDelete) {

    public static TenantLifecycleOptions defaults() {
        return new TenantLifecycleOptions(30, 0, true, false);
    }
}
