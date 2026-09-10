package cn.slainc.captcha.generator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

final class PngBytes {

    private PngBytes() {
    }

    static byte[] of(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            boolean written = ImageIO.write(image, "png", out);
            if (!written) {
                throw new IllegalStateException("当前 JVM 缺少 PNG 编码器，无法生成验证码图片");
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("验证码图片编码失败", exception);
        }
    }

    static String dataUrl(BufferedImage image) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(of(image));
    }
}
