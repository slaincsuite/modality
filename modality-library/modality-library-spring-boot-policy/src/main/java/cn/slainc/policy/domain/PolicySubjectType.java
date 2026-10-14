package cn.slainc.policy.domain;

public enum PolicySubjectType {

    GLOBAL("global"),

    TENANT("tenant"),

    ROLE("role"),

    USER("user");

    private final String code;

    PolicySubjectType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PolicySubjectType of(String code) {
        for (PolicySubjectType type : values()) {
            if (type.code.equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("非法的策略作用对象: " + code + "，只允许 global、tenant、role、user");
    }
}
