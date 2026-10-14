package cn.slainc.storage.image;

import cn.slainc.storage.StorageException;
import cn.slainc.storage.metadata.ImageMetadataReader;
import cn.slainc.storage.spi.ImageMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    @Test
    @DisplayName("缩略图按指定尺寸等比缩放，长边不超过限制")
    void thumbnailKeepsAspectRatio() throws IOException {
        byte[] source = png(400, 200);
        byte[] thumbnail = ImageProcessor.thumbnail(source, 100, 100);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(image.getWidth()).isEqualTo(100);
        assertThat(image.getHeight()).isEqualTo(50);
    }

    @Test
    @DisplayName("裁剪区域与像素内容正确")
    void cropRegionIsCorrect() throws IOException {
        BufferedImage source = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 50, 100);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(50, 0, 50, 100);
        graphics.dispose();

        BufferedImage cropped = ImageProcessor.crop(source, 40, 20, 30, 30);
        assertThat(cropped.getWidth()).isEqualTo(30);
        assertThat(cropped.getHeight()).isEqualTo(30);
        assertThat(new Color(cropped.getRGB(0, 0)).getRed()).isGreaterThan(200);
        assertThat(new Color(cropped.getRGB(29, 29)).getBlue()).isGreaterThan(200);
    }

    @Test
    @DisplayName("裁剪区域越界时给出中文异常")
    void cropOutOfBounds() {
        BufferedImage source = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        assertThatThrownBy(() -> ImageProcessor.crop(source, 5, 5, 10, 10))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("裁剪区域超出图片范围");
    }

    @Test
    @DisplayName("旋转 90 度后宽高互换")
    void rotateSwapsDimensions() {
        BufferedImage source = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        BufferedImage rotated = ImageProcessor.rotate(source, 90);
        assertThat(rotated.getWidth()).isEqualTo(20);
        assertThat(rotated.getHeight()).isEqualTo(40);
    }

    @Test
    @DisplayName("格式转换后可被 ImageIO 读回")
    void convertCanBeReadBack() throws IOException {
        byte[] source = png(60, 30);
        byte[] jpeg = ImageProcessor.convert(source, ImageFormat.JPEG, 0.8f);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(60);
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
            assertThat(ImageIO.getImageReaders(stream).next().getFormatName()).isEqualTo("JPEG");
        }
    }

    @Test
    @DisplayName("JPEG 压缩质量生效：质量越低体积越小")
    void compressQualityAffectsSize() {
        byte[] source = noisyPng(200, 200);
        byte[] high = ImageProcessor.convert(source, ImageFormat.JPEG, 0.95f);
        byte[] low = ImageProcessor.convert(source, ImageFormat.JPEG, 0.1f);
        assertThat(low.length).isLessThan(high.length);
    }

    @Test
    @DisplayName("JDK 无 WebP 写入器时明确抛中文异常，并可用 PNG 降级")
    void webpNotSupportedByDefaultJdk() {
        byte[] source = png(20, 20);
        if (ImageFormat.WEBP.writable()) {
            assertThat(ImageProcessor.convert(source, ImageFormat.WEBP, 0.8f)).isNotEmpty();
            return;
        }
        assertThatThrownBy(() -> ImageProcessor.convert(source, ImageFormat.WEBP, 0.8f))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("未提供 WEBP 写入器");
        ImageProcessor.ConvertedImage converted = ImageProcessor.convertWithFallback(source, ImageFormat.WEBP,
                ImageFormat.PNG, 0.8f);
        assertThat(converted.format()).isEqualTo(ImageFormat.PNG);
        assertThat(converted.data()).isNotEmpty();
    }

    @Test
    @DisplayName("文字水印改变像素且不改变尺寸")
    void textWatermark() {
        byte[] source = png(200, 100);
        BufferedImage marked = ImageProcessor.watermarkText(ImageProcessor.decode(source), "机密", WatermarkPosition.CENTER,
                1f, 20);
        assertThat(marked.getWidth()).isEqualTo(200);
        assertThat(marked.getHeight()).isEqualTo(100);
        BufferedImage original = ImageProcessor.decode(source);
        boolean changed = false;
        for (int x = 0; x < 200 && !changed; x++) {
            for (int y = 0; y < 100; y++) {
                if (marked.getRGB(x, y) != original.getRGB(x, y)) {
                    changed = true;
                    break;
                }
            }
        }
        assertThat(changed).isTrue();
    }

    @Test
    @DisplayName("图片元数据读出宽高、格式与 EXIF")
    void readImageMetadataWithExif() throws IOException {
        byte[] jpeg = jpegWithExif(120, 80);
        ImageMetadata metadata = ImageMetadataReader.read(jpeg);
        assertThat(metadata.width()).isEqualTo(120);
        assertThat(metadata.height()).isEqualTo(80);
        assertThat(metadata.format()).isEqualTo("JPEG");
        Map<String, String> exif = metadata.exif();
        assertThat(exif).containsEntry("Make", "Canon").containsEntry("Model", "EOS R5")
                .containsEntry("Orientation", "6").containsEntry("DateTime", "2026:02:13 10:00:00");
    }

    @Test
    @DisplayName("非图片内容读取元数据给出中文异常")
    void rejectsNonImage() {
        assertThatThrownBy(() -> ImageMetadataReader.read("not an image".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("无法识别的图片格式");
    }

    private static byte[] png(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.ORANGE);
            graphics.fillRect(0, 0, width, height);
            graphics.dispose();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(image, "png", buffer);
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] noisyPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(11);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(image, "png", buffer);
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] jpegWithExif(int width, int height) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ImageIO.write(image, "jpeg", buffer);
        byte[] jpeg = buffer.toByteArray();
        byte[] app1 = exifApp1();
        ByteArrayOutputStream merged = new ByteArrayOutputStream();
        // JFIF APP0 必须是 SOI 之后的第一个标记，因此 EXIF 段插在 APP0 之后
        int insertAt = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            insertAt = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        }
        merged.write(jpeg, 0, insertAt);
        merged.write(0xFF);
        merged.write(0xE1);
        int length = app1.length + 2;
        merged.write((length >> 8) & 0xFF);
        merged.write(length & 0xFF);
        merged.write(app1);
        merged.write(jpeg, insertAt, jpeg.length - insertAt);
        return merged.toByteArray();
    }

    private static byte[] exifApp1() throws IOException {
        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        byte[] make = "Canon\0".getBytes(StandardCharsets.US_ASCII);
        byte[] model = "EOS R5\0".getBytes(StandardCharsets.US_ASCII);
        byte[] dateTime = "2026:02:13 10:00:00\0".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write('I');
        tiff.write('I');
        tiff.write(0x2A);
        tiff.write(0x00);
        tiff.write(0x08);
        tiff.write(0x00);
        tiff.write(0x00);
        tiff.write(0x00);
        tiff.write(0x04);
        tiff.write(0x00);
        int dataOffset = 8 + 2 + 4 * 12 + 4;
        tiff.write(entry(0x010F, 2, make.length, dataOffset));
        tiff.write(entry(0x0110, 2, model.length, dataOffset + make.length));
        tiff.write(entry(0x0112, 3, 1, 6));
        tiff.write(entry(0x0132, 2, dateTime.length, dataOffset + make.length + model.length));
        tiff.write(new byte[]{0x00, 0x00, 0x00, 0x00});
        tiff.write(make);
        tiff.write(model);
        tiff.write(dateTime);
        strings.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        tiff.writeTo(strings);
        return strings.toByteArray();
    }

    private static byte[] entry(int tag, int type, int count, int value) {
        return new byte[]{
                (byte) tag, (byte) (tag >> 8),
                (byte) type, (byte) (type >> 8),
                (byte) count, (byte) (count >> 8), 0x00, 0x00,
                (byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)};
    }
}
