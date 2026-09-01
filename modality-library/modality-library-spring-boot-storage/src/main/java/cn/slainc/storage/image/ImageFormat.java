package cn.slainc.storage.image;

import cn.slainc.core.constant.MimeTypes;
import cn.slainc.storage.StorageException;

import javax.imageio.ImageIO;
import java.util.Locale;

public enum ImageFormat {

    PNG("png", MimeTypes.PNG),
    JPEG("jpg", MimeTypes.JPEG),
    GIF("gif", MimeTypes.GIF),
    BMP("bmp", "image/bmp"),
    WEBP("webp", "image/webp");

    private final String extension;

    private final String contentType;

    ImageFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public boolean writable() {
        return ImageIO.getImageWritersByFormatName(extension).hasNext()
                || ImageIO.getImageWritersByFormatName(name().toLowerCase(Locale.ROOT)).hasNext();
    }

    public void requireWritable() {
        if (!writable()) {
            throw new StorageException("当前 JDK 的 ImageIO 未提供 " + name()
                    + " 写入器，无法写出该格式；请改用 PNG 或 JPEG，或引入对应 ImageIO 插件");
        }
    }

    public static ImageFormat fromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (ImageFormat format : values()) {
            if (format.contentType.equalsIgnoreCase(contentType)) {
                return format;
            }
        }
        return null;
    }

    public static ImageFormat fromFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        for (ImageFormat format : values()) {
            if (lower.endsWith("." + format.extension) || lower.endsWith("." + format.name().toLowerCase(Locale.ROOT))) {
                return format;
            }
        }
        return null;
    }
}
