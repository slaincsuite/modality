package cn.slainc.session;

public enum SessionSortField {

    LOGIN_TIME("登录时间"),
    LAST_ACTIVE_TIME("最后活跃时间"),
    LOGIN_ID("账号"),
    DEVICE_TYPE("设备类型");

    private final String label;

    SessionSortField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
