package cn.slainc.captcha.generator;

import cn.slainc.captcha.CaptchaData;
import cn.slainc.captcha.CaptchaParams;
import cn.slainc.captcha.CaptchaProperties;
import cn.slainc.captcha.CaptchaType;
import cn.slainc.captcha.support.CaptchaFonts;
import com.wf.captcha.base.Captcha;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

public class CaptchaGenerator {

    private static final int CHAR_ROTATION_MAX_DEGREES = 12;

    public CaptchaGenerator() {
    }

    public Generated generate(CaptchaType type, CaptchaProperties properties, CaptchaParams params, int length) {
        return switch (type) {
            case CHAR_NUMBER, CHAR_ALPHA, CHAR_ALNUM -> character(type, properties, params, length);
            case CHINESE -> chinese(properties, params, length);
            case ARITHMETIC -> arithmetic(properties, params);
            case SLIDE -> slide(properties, params);
            case CLICK -> click(properties, params);
        };
    }

    public record Generated(CaptchaType type, String imageBase64, String sliderImageBase64,
                            List<String> words, int width, int height, CaptchaData data, String formula) {
    }

    private Generated character(CaptchaType type, CaptchaProperties properties, CaptchaParams params, int length) {
        int width = firstNotNull(params.width(), properties.getImage().getWidth());
        int height = firstNotNull(params.height(), properties.getImage().getHeight());
        int charType = properties.getImage().getCharType() != null
                ? properties.getImage().getCharType()
                : defaultCharType(type);
        int safeLength = Math.max(4, Math.min(6, length));
        PaintedCharCaptcha captcha = new PaintedCharCaptcha(width, height, safeLength, charType,
                CaptchaFonts.builtin(properties.getImage().getFontIndex(), properties.getImage().getFontSize()),
                parseColor(properties.getImage().getBackground()),
                properties.getImage().getOvalCount(),
                properties.getImage().getLineCount(),
                properties.getImage().getBesselCount(),
                CHAR_ROTATION_MAX_DEGREES);
        CaptchaData data = new CaptchaData(type, captcha.text(), null, null, 0, width, height,
                System.currentTimeMillis());
        return new Generated(type, captcha.toBase64(), null, List.of(), width, height, data, null);
    }

    private Generated chinese(CaptchaProperties properties, CaptchaParams params, int length) {
        int width = firstNotNull(params.width(), properties.getImage().getWidth());
        int height = firstNotNull(params.height(), properties.getImage().getHeight());
        PaintedChineseCaptcha captcha = new PaintedChineseCaptcha(width, height,
                Math.max(4, Math.min(6, length)),
                CaptchaFonts.cjk(Font.BOLD, Math.min(properties.getImage().getFontSize(), 28f)),
                parseColor(properties.getImage().getBackground()),
                properties.getImage().getOvalCount(),
                properties.getImage().getLineCount(),
                properties.getImage().getBesselCount());
        CaptchaData data = new CaptchaData(CaptchaType.CHINESE, captcha.text(), null, null, 0, width, height,
                System.currentTimeMillis());
        return new Generated(CaptchaType.CHINESE, captcha.toBase64(), null, List.of(), width, height, data, null);
    }

    private Generated arithmetic(CaptchaProperties properties, CaptchaParams params) {
        int width = firstNotNull(params.width(), properties.getImage().getWidth());
        int height = firstNotNull(params.height(), properties.getImage().getHeight());
        float fontSize = Math.max(14f, Math.min(24f, properties.getImage().getFontSize() * 0.7f));
        PaintedArithmeticCaptcha captcha = new PaintedArithmeticCaptcha(width, height,
                CaptchaFonts.builtin(properties.getImage().getFontIndex(), fontSize),
                parseColor(properties.getImage().getBackground()),
                properties.getImage().getOvalCount(),
                properties.getImage().getLineCount(),
                properties.getImage().getBesselCount());
        captcha.checkAlpha();
        CaptchaData data = new CaptchaData(CaptchaType.ARITHMETIC, captcha.text(), null, null, 0, width, height,
                System.currentTimeMillis());
        return new Generated(CaptchaType.ARITHMETIC, captcha.toBase64(), null, List.of(), width, height, data,
                captcha.formula());
    }

    private Generated slide(CaptchaProperties properties, CaptchaParams params) {
        int width = firstNotNull(params.width(), properties.getSlide().getWidth());
        int height = firstNotNull(params.height(), properties.getSlide().getHeight());
        int tolerance = firstNotNull(params.slideTolerancePx(), properties.getSlide().getTolerancePx());
        SlidePuzzleImage.Result result = SlidePuzzleImage.draw(width, height, properties.getSlide().getPieceSize());
        CaptchaData data = new CaptchaData(CaptchaType.SLIDE, null, result.gapX(), null, tolerance, width, height,
                System.currentTimeMillis());
        return new Generated(CaptchaType.SLIDE, PngBytes.dataUrl(result.background()),
                PngBytes.dataUrl(result.piece()), List.of(), width, height, data, null);
    }

    private Generated click(CaptchaProperties properties, CaptchaParams params) {
        int width = firstNotNull(params.width(), properties.getClick().getWidth());
        int height = firstNotNull(params.height(), properties.getClick().getHeight());
        int tolerance = firstNotNull(params.clickTolerancePx(), properties.getClick().getTolerancePx());
        ClickCaptchaImage.Result result = ClickCaptchaImage.draw(width, height, properties.getClick().getCharCount());
        CaptchaData data = new CaptchaData(CaptchaType.CLICK, null, null, result.points(), tolerance, width, height,
                System.currentTimeMillis());
        return new Generated(CaptchaType.CLICK, PngBytes.dataUrl(result.image()), null,
                List.of(result.words()), width, height, data, null);
    }

    private int defaultCharType(CaptchaType type) {
        return switch (type) {
            case CHAR_NUMBER -> Captcha.TYPE_ONLY_NUMBER;
            case CHAR_ALPHA -> Captcha.TYPE_ONLY_CHAR;
            default -> Captcha.TYPE_DEFAULT;
        };
    }

    private int firstNotNull(Integer override, int fallback) {
        return override != null ? override : fallback;
    }

    private Color parseColor(String hex) {
        try {
            return Color.decode(hex == null || hex.isBlank() ? "#FFFFFF" : hex.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("验证码背景色配置无效：" + hex, exception);
        }
    }
}
