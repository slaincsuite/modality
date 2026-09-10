package cn.slainc.captcha.generator;

import com.wf.captcha.SpecCaptcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.OutputStream;

class PaintedCharCaptcha extends SpecCaptcha {

    private final Color background;

    private final int ovalCount;

    private final int lineCount;

    private final int besselCount;

    private final int rotationMaxDegrees;

    PaintedCharCaptcha(int width, int height, int len, int charType, Font font, Color background,
                       int ovalCount, int lineCount, int besselCount, int rotationMaxDegrees) {
        setWidth(width);
        setHeight(height);
        setLen(len);
        setCharType(charType);
        setFont(font);
        this.background = background;
        this.ovalCount = ovalCount;
        this.lineCount = lineCount;
        this.besselCount = besselCount;
        this.rotationMaxDegrees = rotationMaxDegrees;
    }

    @Override
    public boolean out(OutputStream out) {
        try {
            out.write(PngBytes.of(render()));
            out.flush();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public String toBase64() {
        return PngBytes.dataUrl(render());
    }

    private BufferedImage render() {
        BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        CaptchaPainting.paintBackground(g, getWidth(), getHeight(), background);
        CaptchaPainting.paintInterference(this, g, ovalCount, lineCount, besselCount);
        CaptchaPainting.paintCharacters(this, g, textChar(), rotationMaxDegrees);
        g.dispose();
        return image;
    }
}
