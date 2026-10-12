package cn.slainc.captcha.generator;

import com.wf.captcha.SpecCaptcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.Random;

import cn.slainc.crypto.random.SecureRandomUtils;

class PaintedArithmeticCaptcha extends SpecCaptcha {

    private static final String[] OPERATORS = {"+", "-", "x", "/"};

    private final Color background;

    private final int ovalCount;

    private final int lineCount;

    private final int besselCount;

    private String formula;

    PaintedArithmeticCaptcha(int width, int height, Font font, Color background,
                             int ovalCount, int lineCount, int besselCount) {
        setWidth(width);
        setHeight(height);
        setLen(2);
        setFont(font);
        this.background = background;
        this.ovalCount = ovalCount;
        this.lineCount = lineCount;
        this.besselCount = besselCount;
    }

    String formula() {
        checkAlpha();
        return formula;
    }

    @Override
    protected char[] alphas() {
        Random random = SecureRandomUtils.secureRandom();
        int operatorIndex = random.nextInt(OPERATORS.length);
        int left;
        int right;
        int answer;
        switch (operatorIndex) {
            case 0 -> {
                left = 10 + random.nextInt(90);
                right = 1 + random.nextInt(89);
                answer = left + right;
            }
            case 1 -> {
                left = 20 + random.nextInt(80);
                right = 1 + random.nextInt(left - 2);
                answer = left - right;
            }
            case 2 -> {
                left = 3 + random.nextInt(10);
                right = 3 + random.nextInt(10);
                answer = left * right;
            }
            default -> {
                right = 2 + random.nextInt(9);
                answer = 2 + random.nextInt(9);
                left = right * answer;
            }
        }
        formula = left + " " + OPERATORS[operatorIndex] + " " + right + " = ?";
        chars = String.valueOf(answer);
        return chars.toCharArray();
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
        CaptchaPainting.paintCenteredText(this, g, formula());
        g.dispose();
        return image;
    }
}
