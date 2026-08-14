package cn.slainc.core.enums;

public enum EnabledStatus implements DictEnum {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int value;
    private final String label;

    EnabledStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public Integer value() {
        return value;
    }

    @Override
    public String label() {
        return label;
    }

    public static EnabledStatus of(Integer value) {
        if (value == null) {
            return null;
        }
        for (EnabledStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return null;
    }

    public boolean enabled() {
        return this == ENABLED;
    }
}
