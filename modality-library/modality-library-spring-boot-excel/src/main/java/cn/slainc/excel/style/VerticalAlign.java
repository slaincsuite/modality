package cn.slainc.excel.style;

public enum VerticalAlign {

    GENERAL("常规"),
    TOP("顶端对齐"),
    CENTER("垂直居中"),
    BOTTOM("底端对齐"),
    JUSTIFY("垂直两端对齐");

    private final String description;

    VerticalAlign(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
