package cn.slainc.captcha.generator;

import com.wf.captcha.base.Captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.ThreadLocalRandom;

final class CaptchaPainting {

    private CaptchaPainting() {
    }

    static Color randomColor() {
        int[] rgb = Captcha.COLOR[ThreadLocalRandom.current().nextInt(Captcha.COLOR.length)];
        return new Color(rgb[0], rgb[1], rgb[2]);
    }

    static void paintBackground(Graphics2D g, int width, int height, Color background) {
        g.setColor(background == null ? Color.WHITE : background);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    static void paintInterference(Captcha captcha, Graphics2D g, int ovalCount, int lineCount, int besselCount) {
        if (ovalCount > 0) {
            captcha.drawOval(ovalCount, g);
        }
        if (lineCount > 0) {
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            captcha.drawLine(lineCount, g);
        }
        if (besselCount > 0) {
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            captcha.drawBesselLine(besselCount, g);
        }
    }

    static void paintCharacters(Captcha captcha, Graphics2D g, char[] chars, int rotationMaxDegrees) {
        g.setFont(captcha.getFont());
        FontMetrics metrics = g.getFontMetrics();
        int slot = Math.max(captcha.getWidth() / chars.length, 1);
        for (int i = 0; i < chars.length; i++) {
            String text = String.valueOf(chars[i]);
            Rectangle2D bounds = metrics.getStringBounds(text, g);
            int x = i * slot + (int) Math.max((slot - bounds.getWidth()) / 2, 2);
            int y = captcha.getHeight() - (int) ((captcha.getHeight() - bounds.getHeight()) / 2) - metrics.getDescent();
            g.setColor(randomColor());
            AffineTransform restore = g.getTransform();
            if (rotationMaxDegrees > 0) {
                double angle = Math.toRadians(Captcha.num(-rotationMaxDegrees, rotationMaxDegrees));
                g.rotate(angle, x + bounds.getWidth() / 2, y - bounds.getHeight() / 2);
            }
            g.drawString(text, x, y);
            g.setTransform(restore);
        }
    }

    static void paintCenteredText(Captcha captcha, Graphics2D g, String text) {
        g.setFont(captcha.getFont());
        FontMetrics metrics = g.getFontMetrics();
        Rectangle2D bounds = metrics.getStringBounds(text, g);
        float x = (float) Math.max((captcha.getWidth() - bounds.getWidth()) / 2, 2);
        float y = (float) ((captcha.getHeight() + bounds.getHeight()) / 2 - metrics.getDescent());
        g.setColor(randomColor());
        g.drawString(text, x, y);
    }
}
