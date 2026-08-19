package cn.slainc.excel.style;

public enum CellAlign {

    GENERAL("常规"),
    LEFT("左对齐"),
    CENTER("居中"),
    RIGHT("右对齐"),
    JUSTIFY("两端对齐"),
    FILL("填充");

    private final String description;

    CellAlign(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
