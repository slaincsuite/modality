package cn.slainc.tenant.quota;

public enum TenantQuotaDimension {

    USERS("用户数", "maxUsers"),
    STORAGE("存储空间", "maxStorage"),
    API_QPS("接口每秒请求数", "maxApiQps"),
    API_DAILY("接口每日请求数", "maxApiDaily");

    private final String displayName;

    private final String property;

    TenantQuotaDimension(String displayName, String property) {
        this.displayName = displayName;
        this.property = property;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProperty() {
        return property;
    }

    public static TenantQuotaDimension of(String value, TenantQuotaDimension defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String text = value.trim();
        for (TenantQuotaDimension dimension : values()) {
            if (dimension.name().equalsIgnoreCase(text) || dimension.property.equalsIgnoreCase(text)) {
                return dimension;
            }
        }
        return defaultValue;
    }
}
