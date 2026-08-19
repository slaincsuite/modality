package cn.slainc.excel.image;

import org.apache.poi.ss.usermodel.Workbook;

public enum PictureFormat {

    PNG(Workbook.PICTURE_TYPE_PNG, "png"),
    JPEG(Workbook.PICTURE_TYPE_JPEG, "jpg"),
    DIB(Workbook.PICTURE_TYPE_DIB, "bmp");

    private final int pictureType;
    private final String extension;

    PictureFormat(int pictureType, String extension) {
        this.pictureType = pictureType;
        this.extension = extension;
    }

    public int pictureType() {
        return pictureType;
    }

    public String extension() {
        return extension;
    }

    public static PictureFormat of(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return PNG;
        }
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return JPEG;
        }
        if (lower.endsWith(".bmp") || lower.endsWith(".dib")) {
            return DIB;
        }
        return PNG;
    }
}
