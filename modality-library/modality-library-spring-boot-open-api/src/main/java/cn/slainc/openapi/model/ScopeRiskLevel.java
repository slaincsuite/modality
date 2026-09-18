package cn.slainc.openapi.model;

public enum ScopeRiskLevel {

    LOW("低"),

    MIDDLE("中"),

    HIGH("高");

    private final String label;

    ScopeRiskLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ScopeRiskLevel of(String code) {
        for (ScopeRiskLevel level : values()) {
            if (level.name().equalsIgnoreCase(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("非法的 Scope 风险等级: " + code + "，只允许 LOW、MIDDLE、HIGH");
    }
}
