package cn.slainc.excel.watermark;

public enum WatermarkKind {

    TEXT("文字水印"),
    IMAGE("图片水印");

    private final String description;

    WatermarkKind(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
