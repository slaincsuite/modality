package cn.slainc.tenant.quota;

public record TenantQuotaCheckResult(boolean allowed, boolean warning, long limit, long used, long requested,
                                     double usageRatio, String message) {

    public static TenantQuotaCheckResult unlimited(long used) {
        return new TenantQuotaCheckResult(true, false, 0L, used, used, 0D, null);
    }

    public static TenantQuotaCheckResult passed(long limit, long used, long requested, double usageRatio) {
        return new TenantQuotaCheckResult(true, false, limit, used, requested, usageRatio, null);
    }

    public static TenantQuotaCheckResult warning(long limit, long used, long requested, double usageRatio,
                                                 String message) {
        return new TenantQuotaCheckResult(true, true, limit, used, requested, usageRatio, message);
    }

    public static TenantQuotaCheckResult exceeded(long limit, long used, long requested, double usageRatio,
                                                  String message) {
        return new TenantQuotaCheckResult(false, false, limit, used, requested, usageRatio, message);
    }

    public boolean unlimited() {
        return limit <= 0L;
    }
}
