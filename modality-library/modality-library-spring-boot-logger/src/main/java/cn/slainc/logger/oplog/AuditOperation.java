package cn.slainc.logger.oplog;

public enum AuditOperation {

    CREATE("新增"),
    UPDATE("修改"),
    DELETE("删除"),
    QUERY("查询"),
    LOGIN("登录"),
    LOGOUT("登出"),
    GRANT("授权"),
    REVOKE("解除授权"),
    IMPORT("导入"),
    EXPORT("导出"),
    CONFIG("配置变更"),
    OTHER("其它");

    private final String label;

    AuditOperation(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
