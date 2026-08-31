package cn.slainc.storage.support;

import cn.slainc.core.constant.MimeTypes;
import cn.slainc.utils.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class ContentTypes {

    private static final int MAGIC_LENGTH = 12;

    private ContentTypes() {
    }

    public static String detect(String filename) {
        String byExtension = FileUtils.mimeTypeByExtension(filename);
        return byExtension == null ? MimeTypes.OCTET_STREAM : byExtension;
    }

    public static String detect(String filename, byte[] header) {
        if (header == null || header.length < 4) {
            return detect(filename);
        }
        String byMagic = FileUtils.mimeTypeByMagicNumber(header);
        return byMagic == null ? detect(filename) : byMagic;
    }

    public static byte[] readHeader(InputStream input) throws IOException {
        byte[] header = new byte[MAGIC_LENGTH];
        int read = input.readNBytes(header, 0, MAGIC_LENGTH);
        if (read <= 0) {
            return new byte[0];
        }
        byte[] actual = new byte[read];
        System.arraycopy(header, 0, actual, 0, read);
        return actual;
    }

    public static boolean isImage(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    public static boolean isVideo(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("video/");
    }

    public static boolean isText(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("text/") || lower.contains("json") || lower.contains("xml")
                || lower.contains("javascript") || lower.contains("csv");
    }
}
