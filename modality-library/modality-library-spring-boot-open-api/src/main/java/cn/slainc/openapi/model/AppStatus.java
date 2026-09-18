package cn.slainc.openapi.model;

public enum AppStatus {

    DRAFT("草稿"),

    REVIEWING("审核中"),

    ONLINE("已上线"),

    OFFLINE("已下线");

    private final String label;

    AppStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
