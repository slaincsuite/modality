package cn.slainc.tenant.isolation;

public enum TenantIsolationStrategy {

    ROW("行级隔离", "共享库表，通过 tenant_id 条件隔离数据"),
    SCHEMA("Schema 隔离", "共享数据库实例，为每个租户创建独立 schema"),
    DATABASE("库级隔离", "为每个租户注册独立数据源");

    private final String displayName;

    private final String description;

    TenantIsolationStrategy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static TenantIsolationStrategy of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        for (TenantIsolationStrategy strategy : values()) {
            if (strategy.name().equalsIgnoreCase(text) || strategy.displayName.equals(text)) {
                return strategy;
            }
        }
        return null;
    }

    public static TenantIsolationStrategy of(String value, TenantIsolationStrategy defaultValue) {
        TenantIsolationStrategy strategy = of(value);
        return strategy == null ? defaultValue : strategy;
    }
}
