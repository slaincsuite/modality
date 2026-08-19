package cn.slainc.excel.style;

public enum BorderWeight {

    NONE("无边框"),
    THIN("细边框"),
    MEDIUM("中等边框"),
    THICK("粗边框"),
    DASHED("虚线边框"),
    DOTTED("点线边框"),
    DOUBLE("双线边框");

    private final String description;

    BorderWeight(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public boolean isVisible() {
        return this != NONE;
    }
}
