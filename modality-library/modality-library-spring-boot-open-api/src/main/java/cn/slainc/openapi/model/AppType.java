package cn.slainc.openapi.model;

public enum AppType {

    WEB("网页应用"),

    MOBILE("移动应用"),

    SERVER("服务端调用"),

    DESKTOP("桌面端");

    private final String label;

    AppType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
