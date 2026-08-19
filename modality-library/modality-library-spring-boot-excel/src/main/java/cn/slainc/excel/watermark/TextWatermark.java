package cn.slainc.excel.watermark;

public record TextWatermark(String text, String fontName, int fontSize, String color, int alphaPercent, int rotation,
                            int tileRows, int tileColumns) {

    public TextWatermark {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("水印文字不能为空");
        }
        if (fontSize < 1 || fontSize > 409) {
            throw new IllegalArgumentException("水印字号必须在 1 到 409 之间");
        }
        if (alphaPercent < 0 || alphaPercent > 100) {
            throw new IllegalArgumentException("水印透明度必须在 0 到 100 之间");
        }
        if (rotation < -90 || rotation > 90) {
            throw new IllegalArgumentException("水印旋转角度必须在 -90 到 90 之间");
        }
        if (tileRows < 1 || tileColumns < 1) {
            throw new IllegalArgumentException("水印平铺尺寸必须大于 0");
        }
        fontName = fontName == null || fontName.isBlank() ? "宋体" : fontName;
        color = color == null || color.isBlank() ? "#808080" : color;
    }

    public static TextWatermark of(String text) {
        return new TextWatermark(text, "宋体", 28, "#808080", 30, -45, 8, 3);
    }

    public TextWatermark withFont(String name, int size) {
        return new TextWatermark(text, name, size, color, alphaPercent, rotation, tileRows, tileColumns);
    }

    public TextWatermark withColor(String value) {
        return new TextWatermark(text, fontName, fontSize, value, alphaPercent, rotation, tileRows, tileColumns);
    }

    public TextWatermark withAlphaPercent(int value) {
        return new TextWatermark(text, fontName, fontSize, color, value, rotation, tileRows, tileColumns);
    }

    public TextWatermark withRotation(int value) {
        return new TextWatermark(text, fontName, fontSize, color, alphaPercent, value, tileRows, tileColumns);
    }

    public TextWatermark withTile(int rows, int columns) {
        return new TextWatermark(text, fontName, fontSize, color, alphaPercent, rotation, rows, columns);
    }
}
