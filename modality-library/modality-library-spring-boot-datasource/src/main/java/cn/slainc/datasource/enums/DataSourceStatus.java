package cn.slainc.datasource.enums;

public enum DataSourceStatus {

    ENABLED("启用"),
    DISABLED("停用"),
    DEGRADED("降级"),
    UNKNOWN("未知");

    private final String description;

    DataSourceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
