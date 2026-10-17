package cn.slainc.modules.auth.service.qrcode;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.auth.config.AuthProperties;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

public class QrImageService {

    private final AuthProperties properties;

    public QrImageService(AuthProperties properties) {
        this.properties = properties;
    }

    public String renderBase64(String content) {
        try {
            int width = properties.getQr().getWidth();
            int height = properties.getQr().getHeight();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN, 1,
                    EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            if (properties.getQr().isLogoEnabled()) {
                overlayLogo(image);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("二维码生成失败：" + e.getMessage());
        }
    }

    // 高纠错等级下中心区域约 1/5 可被 Logo 覆盖而不影响扫码
    private void overlayLogo(BufferedImage image) {
        int width = image.getWidth();
        int logoSize = width / 5;
        int x = (width - logoSize) / 2;
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fill(new RoundRectangle2D.Float(x - 4, x - 4, logoSize + 8, logoSize + 8, 12, 12));
            graphics.setColor(new Color(0x1F, 0x3A, 0x5F));
            graphics.fill(new RoundRectangle2D.Float(x, x, logoSize, logoSize, 10, 10));
            graphics.setColor(Color.WHITE);
            String text = properties.getQr().getLogoText();
            int fontSize = logoSize * 2 / 3;
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            int textWidth = graphics.getFontMetrics().stringWidth(text);
            int ascent = graphics.getFontMetrics().getAscent();
            graphics.drawString(text, x + (logoSize - textWidth) / 2, x + (logoSize + ascent) / 2 - 4);
        } finally {
            graphics.dispose();
        }
    }
}
