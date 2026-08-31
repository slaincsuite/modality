package cn.slainc.storage.support;

public final class StorageConfigKeys {

    public static final String PREFIX = "modality.storage";

    public static final String BACKEND = PREFIX + ".backend";

    public static final String DUAL_WRITE_ENABLED = PREFIX + ".dual-write-enabled";

    public static final String DUAL_WRITE_BACKEND = PREFIX + ".dual-write-backend";

    public static final String TENANT_QUOTA_PREFIX = PREFIX + ".quota.tenant-limit.";

    public static final String USER_QUOTA_PREFIX = PREFIX + ".quota.user-limit.";

    public static final String LIFECYCLE_ENABLED = PREFIX + ".lifecycle.enabled";

    private StorageConfigKeys() {
    }
}
