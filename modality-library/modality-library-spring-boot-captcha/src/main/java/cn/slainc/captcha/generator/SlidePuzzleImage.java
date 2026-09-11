package cn.slainc.captcha.generator;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.ThreadLocalRandom;

final class SlidePuzzleImage {

    private static final int ARC = 14;

    private SlidePuzzleImage() {
    }

    record Result(BufferedImage background, BufferedImage piece, int gapX, int gapY, int pieceSize) {
    }

    static Result draw(int width, int height, int pieceSize) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int size = Math.max(Math.min(pieceSize, Math.min(width, height) / 2), 16);
        int gapX = size + 8 + random.nextInt(Math.max(width - size * 2 - 16, 1));
        int gapY = 8 + random.nextInt(Math.max(height - size - 16, 1));

        BufferedImage background = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = background.createGraphics();
        paintNoisyBackground(g, width, height, random);
        g.dispose();

        RoundRectangle2D shape = new RoundRectangle2D.Double(gapX, gapY, size, size, ARC, ARC);
        BufferedImage piece = new BufferedImage(size, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < size; x++) {
            int sourceX = gapX + x;
            if (sourceX >= width) {
                break;
            }
            for (int y = 0; y < height; y++) {
                if (!shape.contains(sourceX, y)) {
                    continue;
                }
                int rgb = background.getRGB(sourceX, y);
                piece.setRGB(x, y, 0xFF000000 | rgb);
                background.setRGB(sourceX, y, darken(rgb));
            }
        }

        Graphics2D overlay = background.createGraphics();
        overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        overlay.setStroke(new BasicStroke(2f));
        overlay.setColor(new Color(0, 0, 0, 110));
        overlay.draw(shape);
        overlay.setColor(new Color(255, 255, 255, 70));
        overlay.draw(new RoundRectangle2D.Double(gapX + 2, gapY + 2, size - 4, size - 4, ARC, ARC));
        overlay.dispose();
        return new Result(background, piece, gapX, gapY, size);
    }

    private static void paintNoisyBackground(Graphics2D g, int width, int height, ThreadLocalRandom random) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color start = Color.getHSBColor(random.nextFloat(), 0.45f, 0.75f);
        Color end = Color.getHSBColor(random.nextFloat(), 0.45f, 0.55f);
        g.setPaint(new GradientPaint(0, 0, start, width, height, end));
        g.fillRect(0, 0, width, height);
        for (int i = 0; i < 26; i++) {
            g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), 90));
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            switch (random.nextInt(3)) {
                case 0 -> {
                    int radius = 6 + random.nextInt(18);
                    g.fillOval(x - radius / 2, y - radius / 2, radius, radius);
                }
                case 1 -> g.fillRect(x, y, 8 + random.nextInt(30), 4 + random.nextInt(10));
                default -> g.drawLine(x, y, random.nextInt(width), random.nextInt(height));
            }
        }
        g.setColor(new Color(255, 255, 255, 70));
        for (int i = 0; i < width * height / 40; i++) {
            g.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
        }
    }

    private static int darken(int rgb) {
        Color color = new Color(rgb, false);
        return new Color((color.getRed() * 5) / 8, (color.getGreen() * 5) / 8, (color.getBlue() * 5) / 8).getRGB();
    }
}
