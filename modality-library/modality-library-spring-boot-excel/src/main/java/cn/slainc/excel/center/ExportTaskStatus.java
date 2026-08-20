package cn.slainc.excel.center;

public enum ExportTaskStatus {

    PENDING("等待中"),
    RUNNING("执行中"),
    SUCCEEDED("已完成"),
    FAILED("已失败"),
    CANCELED("已取消");

    private final String description;

    ExportTaskStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }

    public boolean isResumable() {
        return this == RUNNING || this == FAILED || this == PENDING;
    }

    public static ExportTaskStatus of(String name) {
        if (name == null || name.isBlank()) {
            return PENDING;
        }
        return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
