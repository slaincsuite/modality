package cn.slainc.captcha.generator;

import cn.slainc.captcha.support.CaptchaFonts;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import cn.slainc.crypto.random.SecureRandomUtils;

final class ClickCaptchaImage {

    private static final String CHAR_POOL = "山石田火木风云雨电日月光水江河海洋城路桥门窗口手足马牛羊鸟鱼虫春秋冬雪花树林草叶金玉";

    private ClickCaptchaImage() {
    }

    record Result(BufferedImage image, int[][] points, String[] words) {
    }

    static Result draw(int width, int height, int charCount) {
        Random random = SecureRandomUtils.secureRandom();
        int count = Math.max(3, Math.min(4, charCount));
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < CHAR_POOL.length(); i++) {
            pool.add(i);
        }
        Collections.shuffle(pool, random);
        char[] chosen = new char[count];
        for (int i = 0; i < count; i++) {
            chosen[i] = CHAR_POOL.charAt(pool.get(i));
        }

        List<int[]> cells = new ArrayList<>();
        int cellWidth = width / 2;
        int cellHeight = height / 2;
        for (int col = 0; col < 2; col++) {
            for (int row = 0; row < 2; row++) {
                cells.add(new int[]{col * cellWidth + cellWidth / 2, row * cellHeight + cellHeight / 2});
            }
        }
        Collections.shuffle(cells, random);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        paintBackground(g, width, height, random);

        int[][] points = new int[count][2];
        String[] words = new String[count];
        for (int i = 0; i < count; i++) {
            int[] cell = cells.get(i);
            int x = cell[0] + random.nextInt(Math.max(cellWidth / 4, 1)) - cellWidth / 8;
            int y = cell[1] + random.nextInt(Math.max(cellHeight / 4, 1)) - cellHeight / 8;
            points[i] = new int[]{x, y};
            words[i] = String.valueOf(chosen[i]);
            drawCharacter(g, words[i], x, y, random);
        }
        g.dispose();
        return new Result(image, points, words);
    }

    private static void drawCharacter(Graphics2D g, String word, int x, int y, Random random) {
        Font font = CaptchaFonts.cjk(Font.BOLD, 22 + random.nextInt(9));
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        Rectangle2D bounds = metrics.getStringBounds(word, g);
        g.setColor(Color.getHSBColor(random.nextFloat(), 0.65f, 0.38f));
        AffineTransform restore = g.getTransform();
        g.rotate(Math.toRadians(random.nextInt(41) - 20), x, y);
        g.drawString(word, (float) (x - bounds.getWidth() / 2), (float) (y + (metrics.getAscent() - metrics.getDescent()) / 2));
        g.setTransform(restore);
    }

    private static void paintBackground(Graphics2D g, int width, int height, Random random) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color start = Color.getHSBColor(random.nextFloat(), 0.22f, 0.94f);
        Color end = Color.getHSBColor(random.nextFloat(), 0.22f, 0.82f);
        g.setPaint(new GradientPaint(0, 0, start, width, height, end));
        g.fillRect(0, 0, width, height);
        for (int i = 0; i < 14; i++) {
            g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), 40));
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            int radius = 5 + random.nextInt(16);
            if (random.nextInt(2) == 0) {
                g.fillOval(x, y, radius, radius);
            } else {
                g.drawLine(x, y, random.nextInt(width), random.nextInt(height));
            }
        }
    }
}
