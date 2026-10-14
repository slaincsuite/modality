package cn.slainc.logger.oplog;

public enum AuditLevel {

    INFO("普通"),
    WARN("敏感"),
    ERROR("失败"),
    CRITICAL("关键");

    private final String label;

    AuditLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
