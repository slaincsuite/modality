package cn.slainc.core.enums;

public enum LoginType implements DictEnum {

    ADMIN("ADMIN", "管理端"),
    USER("USER", "用户端"),
    CLIENT("CLIENT", "客户端");

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
        if (value == null) {
            return null;
        }
        for (LoginType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
