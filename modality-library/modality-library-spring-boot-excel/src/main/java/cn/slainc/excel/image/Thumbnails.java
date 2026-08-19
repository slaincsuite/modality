package cn.slainc.excel.image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Thumbnails {

    static {
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    private Thumbnails() {
    }

    public static byte[] of(Path source, int maxWidth, int maxHeight) {
        try (InputStream inputStream = Files.newInputStream(source)) {
            return of(inputStream.readAllBytes(), maxWidth, maxHeight);
        } catch (IOException ex) {
            throw new IllegalStateException("读取图片失败: " + source, ex);
        }
    }

    public static byte[] of(byte[] source, int maxWidth, int maxHeight) {
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("缩略图宽高必须大于 0");
        }
        BufferedImage original = read(source);
        int[] size = fit(original.getWidth(), original.getHeight(), maxWidth, maxHeight);
        BufferedImage scaled = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, size[0], size[1], null);
        } finally {
            graphics.dispose();
        }
        return write(scaled);
    }

    public static byte[] scale(byte[] source, double factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("缩放比例必须大于 0");
        }
        BufferedImage original = read(source);
        int width = Math.max(1, (int) Math.round(original.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(original.getHeight() * factor));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return write(scaled);
    }

    public static int[] sizeOf(byte[] source) {
        BufferedImage image = read(source);
        return new int[]{image.getWidth(), image.getHeight()};
    }

    static int[] fit(int width, int height, int maxWidth, int maxHeight) {
        double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);
        if (ratio >= 1d) {
            return new int[]{width, height};
        }
        return new int[]{Math.max(1, (int) Math.round(width * ratio)), Math.max(1, (int) Math.round(height * ratio))};
    }

    private static BufferedImage read(byte[] source) {
        if (source == null || source.length == 0) {
            throw new IllegalArgumentException("图片内容不能为空");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw new IllegalArgumentException("无法识别的图片格式");
            }
            return image;
        } catch (IOException ex) {
            throw new IllegalStateException("解析图片失败", ex);
        }
    }

    private static byte[] write(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", outputStream)) {
                throw new IllegalStateException("生成缩略图失败");
            }
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成缩略图失败", ex);
        }
    }
}
