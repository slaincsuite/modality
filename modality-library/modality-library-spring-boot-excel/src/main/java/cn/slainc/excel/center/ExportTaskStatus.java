package cn.slainc.excel.center;

public enum ExportTaskStatus {

    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED;

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
