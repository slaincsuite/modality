package cn.slainc.tenant.enums;

public enum TenantStatus {

    TRIAL("试用"),
    ACTIVE("正常"),
    EXPIRED("过期"),
    FROZEN("冻结"),
    DISABLED("停用");

    private final String displayName;

    TenantStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isUsable() {
        return this == TRIAL || this == ACTIVE;
    }

    public static TenantStatus of(String value, TenantStatus defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String text = value.trim();
        for (TenantStatus status : values()) {
            if (status.name().equalsIgnoreCase(text) || status.displayName.equals(text)) {
                return status;
            }
        }
        return defaultValue;
    }
}
