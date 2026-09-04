package cn.slainc.monitor.alert;

public enum AlertLevel {

    INFO("提示"), WARN("警告"), ERROR("严重");

    private final String description;

    AlertLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static AlertLevel parse(String value, AlertLevel defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        for (AlertLevel level : values()) {
            if (level.name().equalsIgnoreCase(value.trim())) {
                return level;
            }
        }
        return defaultValue;
    }
}
