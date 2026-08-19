package cn.slainc.excel.watermark;

public record WatermarkShape(WatermarkKind kind, String text, int rotation, int alphaPercent, byte[] imageData) {

    public static WatermarkShape text(String text, int rotation, int alphaPercent) {
        return new WatermarkShape(WatermarkKind.TEXT, text, rotation, alphaPercent, null);
    }

    public static WatermarkShape image(byte[] imageData, int rotation, int alphaPercent) {
        return new WatermarkShape(WatermarkKind.IMAGE, null, rotation, alphaPercent, imageData);
    }

    public boolean isText() {
        return kind == WatermarkKind.TEXT;
    }
}
