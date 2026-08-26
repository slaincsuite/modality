package cn.slainc.tenant.quota;

public class TenantQuota {

    private Long tenantId;

    private long maxUsers;

    private long maxStorage;

    private long maxApiQps;

    private long maxApiDaily;

    public static TenantQuota of(long maxUsers, long maxStorage, long maxApiQps, long maxApiDaily) {
        TenantQuota quota = new TenantQuota();
        quota.setMaxUsers(maxUsers);
        quota.setMaxStorage(maxStorage);
        quota.setMaxApiQps(maxApiQps);
        quota.setMaxApiDaily(maxApiDaily);
        return quota;
    }

    public long limitOf(TenantQuotaDimension dimension) {
        return switch (dimension) {
            case USERS -> maxUsers;
            case STORAGE -> maxStorage;
            case API_QPS -> maxApiQps;
            case API_DAILY -> maxApiDaily;
        };
    }

    public void limit(TenantQuotaDimension dimension, long value) {
        switch (dimension) {
            case USERS -> maxUsers = value;
            case STORAGE -> maxStorage = value;
            case API_QPS -> maxApiQps = value;
            case API_DAILY -> maxApiDaily = value;
        }
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public long getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(long maxUsers) {
        this.maxUsers = maxUsers;
    }

    public long getMaxStorage() {
        return maxStorage;
    }

    public void setMaxStorage(long maxStorage) {
        this.maxStorage = maxStorage;
    }

    public long getMaxApiQps() {
        return maxApiQps;
    }

    public void setMaxApiQps(long maxApiQps) {
        this.maxApiQps = maxApiQps;
    }

    public long getMaxApiDaily() {
        return maxApiDaily;
    }

    public void setMaxApiDaily(long maxApiDaily) {
        this.maxApiDaily = maxApiDaily;
    }
}
