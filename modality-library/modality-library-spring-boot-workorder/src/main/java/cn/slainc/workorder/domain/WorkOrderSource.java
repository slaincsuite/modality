package cn.slainc.workorder.domain;


public enum WorkOrderSource {

    MANUAL("手动"),
    API("API"),
    SYSTEM("系统"),
    TIMER("定时");

    private final String description;

    WorkOrderSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
