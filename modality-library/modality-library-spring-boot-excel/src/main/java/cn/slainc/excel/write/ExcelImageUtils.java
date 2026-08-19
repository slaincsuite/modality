package cn.slainc.excel.write;

import cn.idev.excel.metadata.data.ImageData;
import cn.idev.excel.metadata.data.WriteCellData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.List;

public final class ExcelImageUtils {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private ExcelImageUtils() {
    }

    public static byte[] fromBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        String content = base64.trim();
        int comma = content.indexOf(',');
        if (content.startsWith("data:") && comma > 0) {
            content = content.substring(comma + 1);
        }
        return Base64.getDecoder().decode(content);
    }

    public static byte[] fromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try (InputStream inputStream = URI.create(url.trim()).toURL().openStream()) {
            return readLimited(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("下载图片失败: " + url, ex);
        }
    }

    public static WriteCellData<Void> toCellData(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        ImageData imageData = new ImageData();
        imageData.setImage(imageBytes);
        WriteCellData<Void> cellData = new WriteCellData<>();
        cellData.setImageDataList(List.of(imageData));
        return cellData;
    }

    public static WriteCellData<Void> toCellData(byte[] imageBytes, int width, int height) {
        WriteCellData<Void> cellData = toCellData(imageBytes);
        if (cellData != null && (width > 0 || height > 0)) {
            ImageData imageData = cellData.getImageDataList().get(0);
            imageData.setRelativeFirstRowIndex(0);
            imageData.setRelativeLastRowIndex(Math.max(0, height - 1));
            imageData.setRelativeFirstColumnIndex(0);
            imageData.setRelativeLastColumnIndex(Math.max(0, width - 1));
        }
        return cellData;
    }

    public static boolean isImage(String filename) {
        return cn.slainc.utils.io.FileUtils.hasExtension(filename, "png", "jpg", "jpeg", "gif", "bmp", "webp");
    }

    private static byte[] readLimited(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        int total = 0;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IOException("图片超过 " + (MAX_BYTES / 1024 / 1024) + " MB 上限");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
