package cn.slainc.workorder.domain;


public enum WorkOrderStateKind {

    INITIAL("初始"),
    INTERMEDIATE("中间"),
    TERMINAL("终态");

    private final String description;

    WorkOrderStateKind(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
