package cn.slainc.monitor.alert;

public enum AlertType {

    TRIGGER("触发"), RECOVER("恢复");

    private final String description;

    AlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
