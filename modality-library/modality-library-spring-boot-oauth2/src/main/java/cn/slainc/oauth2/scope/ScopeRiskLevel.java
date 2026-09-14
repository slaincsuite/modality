package cn.slainc.oauth2.scope;

public enum ScopeRiskLevel {

    LOW("低风险"),

    MIDDLE("中风险"),

    HIGH("高风险");

    private final String label;

    ScopeRiskLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ScopeRiskLevel of(String value) {
        if (value == null || value.isBlank()) {
            return LOW;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LOW;
        }
    }
}
