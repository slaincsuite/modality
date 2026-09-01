package cn.slainc.storage.image;

import cn.slainc.storage.StorageException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

public final class ImageProcessor {

    private ImageProcessor() {
    }

    public static BufferedImage decode(byte[] source) {
        if (source == null || source.length == 0) {
            throw new StorageException("图片内容为空，无法处理");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw new StorageException("无法解码图片内容，可能不是受支持的图片格式");
            }
            return image;
        } catch (IOException ex) {
            throw new StorageException("解码图片失败", ex);
        }
    }

    public static byte[] thumbnail(byte[] source, int maxWidth, int maxHeight) {
        return encode(scale(decode(source), maxWidth, maxHeight), ImageFormat.PNG, 1f);
    }

    public static byte[] thumbnail(byte[] source, int maxWidth, int maxHeight, ImageFormat format, float quality) {
        return encode(scale(decode(source), maxWidth, maxHeight), format, quality);
    }

    public static BufferedImage scale(BufferedImage source, int maxWidth, int maxHeight) {
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new StorageException("缩略图尺寸必须为正数，当前为 " + maxWidth + "x" + maxHeight);
        }
        double ratio = Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight());
        if (ratio >= 1d && maxWidth >= source.getWidth() && maxHeight >= source.getHeight()) {
            return copy(source, source.getWidth(), source.getHeight());
        }
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        return copy(source, width, height);
    }

    public static BufferedImage resize(BufferedImage source, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new StorageException("目标尺寸必须为正数，当前为 " + width + "x" + height);
        }
        return copy(source, width, height);
    }

    public static BufferedImage crop(BufferedImage source, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new StorageException("裁剪宽高必须为正数，当前为 " + width + "x" + height);
        }
        if (x < 0 || y < 0 || x + width > source.getWidth() || y + height > source.getHeight()) {
            throw new StorageException("裁剪区域超出图片范围：图片 " + source.getWidth() + "x" + source.getHeight()
                    + "，裁剪 (" + x + "," + y + " " + width + "x" + height + ")");
        }
        BufferedImage target = new BufferedImage(width, height, source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, x, y, x + width, y + height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    public static BufferedImage rotate(BufferedImage source, int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized == 0) {
            return copy(source, source.getWidth(), source.getHeight());
        }
        boolean quarter = normalized == 90 || normalized == 270;
        int width = quarter ? source.getHeight() : source.getWidth();
        int height = quarter ? source.getWidth() : source.getHeight();
        BufferedImage target = new BufferedImage(width, height, source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform transform = new AffineTransform();
            transform.translate(width / 2d, height / 2d);
            transform.rotate(Math.toRadians(normalized));
            transform.translate(-source.getWidth() / 2d, -source.getHeight() / 2d);
            graphics.drawRenderedImage(source, transform);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    public static BufferedImage watermarkText(BufferedImage source, String text, WatermarkPosition position,
                                              float alpha, int fontSize) {
        if (text == null || text.isBlank()) {
            throw new StorageException("水印文字不能为空");
        }
        BufferedImage target = copy(source, source.getWidth(), source.getHeight());
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(1, fontSize)));
            graphics.setColor(new Color(255, 255, 255, Math.round(Math.max(0f, Math.min(1f, alpha)) * 255)));
            if (position == WatermarkPosition.TILE) {
                graphics.rotate(Math.toRadians(-30), target.getWidth() / 2d, target.getHeight() / 2d);
                for (int y = -target.getHeight(); y < target.getHeight() * 2; y += fontSize * 6) {
                    for (int x = -target.getWidth(); x < target.getWidth() * 2; x += fontSize * 8) {
                        graphics.drawString(text, x, y);
                    }
                }
            } else {
                int textWidth = graphics.getFontMetrics().stringWidth(text);
                int[] point = anchor(position, target.getWidth(), target.getHeight(), textWidth, fontSize);
                graphics.drawString(text, point[0], point[1]);
            }
        } finally {
            graphics.dispose();
        }
        return target;
    }

    public static BufferedImage watermarkImage(BufferedImage source, BufferedImage watermark,
                                               WatermarkPosition position, float alpha) {
        if (watermark == null) {
            throw new StorageException("水印图片不能为空");
        }
        BufferedImage target = copy(source, source.getWidth(), source.getHeight());
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.max(0f, Math.min(1f, alpha))));
            if (position == WatermarkPosition.TILE) {
                for (int y = 0; y < target.getHeight(); y += watermark.getHeight() * 2) {
                    for (int x = 0; x < target.getWidth(); x += watermark.getWidth() * 2) {
                        graphics.drawImage(watermark, x, y, null);
                    }
                }
            } else {
                int[] point = anchor(position, target.getWidth(), target.getHeight(), watermark.getWidth(),
                        watermark.getHeight());
                graphics.drawImage(watermark, point[0], point[1], null);
            }
        } finally {
            graphics.dispose();
        }
        return target;
    }

    public static byte[] encode(BufferedImage image, ImageFormat format, float quality) {
        format.requireWritable();
        BufferedImage source = toCompatible(image, format);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format.extension());
        if (!writers.hasNext()) {
            writers = ImageIO.getImageWritersByFormatName(format.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (!writers.hasNext()) {
            throw new StorageException("当前 JDK 的 ImageIO 未提供 " + format.name() + " 写入器");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0f, Math.min(1f, quality)));
            }
            writer.write(null, new IIOImage(source, null, null), param);
        } catch (IOException ex) {
            throw new StorageException("编码图片失败：" + format.name(), ex);
        } finally {
            writer.dispose();
        }
        return buffer.toByteArray();
    }

    public static byte[] convert(byte[] source, ImageFormat format, float quality) {
        return encode(decode(source), format, quality);
    }

    public static ConvertedImage convertWithFallback(byte[] source, ImageFormat preferred, ImageFormat fallback,
                                                     float quality) {
        if (preferred.writable()) {
            return new ConvertedImage(encode(decode(source), preferred, quality), preferred);
        }
        return new ConvertedImage(encode(decode(source), fallback, quality), fallback);
    }

    private static BufferedImage toCompatible(BufferedImage image, ImageFormat format) {
        boolean alpha = image.getColorModel().hasAlpha();
        if (format == ImageFormat.JPEG && alpha) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            return rgb;
        }
        return image;
    }

    private static int[] anchor(WatermarkPosition position, int width, int height, int markWidth, int markHeight) {
        int margin = Math.max(8, Math.min(width, height) / 40);
        return switch (position) {
            case TOP_LEFT -> new int[]{margin, margin + markHeight};
            case TOP_RIGHT -> new int[]{width - markWidth - margin, margin + markHeight};
            case BOTTOM_LEFT -> new int[]{margin, height - margin};
            case BOTTOM_RIGHT -> new int[]{width - markWidth - margin, height - margin};
            default -> new int[]{(width - markWidth) / 2, (height + markHeight) / 2};
        };
    }

    private static BufferedImage copy(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    public record ConvertedImage(byte[] data, ImageFormat format) {
    }
}
