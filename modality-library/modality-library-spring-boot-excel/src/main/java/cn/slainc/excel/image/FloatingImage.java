package cn.slainc.excel.image;

public record FloatingImage(byte[] data, PictureFormat format, ImageAnchor anchor) {

    public FloatingImage {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("图片内容不能为空");
        }
        format = format == null ? PictureFormat.PNG : format;
        anchor = anchor == null ? ImageAnchor.cell(0, 0) : anchor;
    }

    public static FloatingImage of(byte[] data) {
        return new FloatingImage(data, PictureFormat.PNG, ImageAnchor.cell(0, 0));
    }

    public FloatingImage withFormat(PictureFormat value) {
        return new FloatingImage(data, value, anchor);
    }

    public FloatingImage withAnchor(ImageAnchor value) {
        return new FloatingImage(data, format, value);
    }
}
