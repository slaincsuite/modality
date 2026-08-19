package cn.slainc.excel.watermark;

import cn.slainc.excel.image.PictureFormat;

public record ImageWatermark(byte[] data, PictureFormat format, int alphaPercent, int tileRows, int tileColumns) {

    public ImageWatermark {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("水印图片内容不能为空");
        }
        if (alphaPercent < 0 || alphaPercent > 100) {
            throw new IllegalArgumentException("水印透明度必须在 0 到 100 之间");
        }
        if (tileRows < 1 || tileColumns < 1) {
            throw new IllegalArgumentException("水印平铺尺寸必须大于 0");
        }
        format = format == null ? PictureFormat.PNG : format;
    }

    public static ImageWatermark of(byte[] data) {
        return new ImageWatermark(data, PictureFormat.PNG, 40, 10, 4);
    }

    public ImageWatermark withFormat(PictureFormat value) {
        return new ImageWatermark(data, value, alphaPercent, tileRows, tileColumns);
    }

    public ImageWatermark withAlphaPercent(int value) {
        return new ImageWatermark(data, format, value, tileRows, tileColumns);
    }

    public ImageWatermark withTile(int rows, int columns) {
        return new ImageWatermark(data, format, alphaPercent, rows, columns);
    }
}
