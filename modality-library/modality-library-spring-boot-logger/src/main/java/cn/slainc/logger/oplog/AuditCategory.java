package cn.slainc.logger.oplog;

public enum AuditCategory {

    LOGIN("登录"),
    PERMISSION("权限"),
    DATA("数据"),
    SYSTEM("系统"),
    SECURITY("安全");

    private final String label;

    AuditCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
