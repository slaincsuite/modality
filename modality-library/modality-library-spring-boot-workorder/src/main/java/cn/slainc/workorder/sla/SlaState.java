package cn.slainc.workorder.sla;


public enum SlaState {

    ON_TRACK("正常"),
    WARNING("临期预警"),
    BREACHED("已超时");

    private final String description;

    SlaState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
