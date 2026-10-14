package cn.slainc.policy.domain;

public enum PolicyStatus {

    ENABLED("enabled"),

    DISABLED("disabled");

    private final String code;

    PolicyStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PolicyStatus of(String code) {
        for (PolicyStatus status : values()) {
            if (status.code.equalsIgnoreCase(code) || status.name().equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("非法的策略状态: " + code + "，只允许 enabled 或 disabled");
    }
}
