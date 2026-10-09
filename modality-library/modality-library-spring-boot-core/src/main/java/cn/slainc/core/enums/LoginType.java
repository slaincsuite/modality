package cn.slainc.core.enums;

public enum LoginType implements DictEnum {

    PLATFORM_ADMIN("PLATFORM_ADMIN", "平台级超级管理员"),

    PLATFORM_USER("PLATFORM_USER", "平台级用户"),

    TENANT_ADMIN("TENANT_ADMIN", "租户级超级管理员"),

    TENANT_USER("TENANT_USER", "租户级用户"),

    APP("APP", "应用方用户"),

    SERVICE("SERVICE", "服务方用户"),

    GUEST("GUEST", "访客");

    private final String value;

    private final String label;

    LoginType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public String label() {
        return label;
    }

    public static LoginType of(String value) {
        return DictEnums.of(LoginType.class, value);
    }
}
