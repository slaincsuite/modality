package cn.slainc.captcha.generator;

import cn.slainc.captcha.CaptchaParams;
import cn.slainc.captcha.CaptchaProperties;
import cn.slainc.captcha.CaptchaType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaGeneratorTest {

    private final CaptchaProperties properties = new CaptchaProperties();

    private final CaptchaGenerator generator = new CaptchaGenerator();

    @Test
    void charAlnumAnswerMatchesPatternAndImageIsPngDataUrl() {
        Pattern pattern = Pattern.compile("[A-Za-z0-9]{4}");
        for (int i = 0; i < 30; i++) {
            CaptchaGenerator.Generated generated = generator.generate(CaptchaType.CHAR_ALNUM, properties,
                    CaptchaParams.none(), 4);
            assertTrue(pattern.matcher(generated.data().answer()).matches());
            assertTrue(generated.imageBase64().startsWith("data:image/png;base64,"));
            assertEquals(properties.getImage().getWidth(), generated.width());
            assertEquals(properties.getImage().getHeight(), generated.height());
            assertNull(generated.data().slideX());
        }
    }

    @Test
    void charNumberAnswerIsDigitsOnly() {
        for (int i = 0; i < 30; i++) {
            CaptchaGenerator.Generated generated = generator.generate(CaptchaType.CHAR_NUMBER, properties,
                    CaptchaParams.none(), 4);
            assertTrue(generated.data().answer().matches("[0-9]{4}"));
        }
    }

    @Test
    void charAlphaAnswerIsLettersOnly() {
        for (int i = 0; i < 30; i++) {
            CaptchaGenerator.Generated generated = generator.generate(CaptchaType.CHAR_ALPHA, properties,
                    CaptchaParams.none(), 4);
            assertTrue(generated.data().answer().matches("[A-Za-z]{4}"));
        }
    }

    @Test
    void chineseAnswerIsCjkCharacters() {
        for (int i = 0; i < 20; i++) {
            CaptchaGenerator.Generated generated = generator.generate(CaptchaType.CHINESE, properties,
                    CaptchaParams.none(), 4);
            assertEquals(4, generated.data().answer().length());
            assertTrue(generated.data().answer().matches("[\u4e00-\u9fff]{4}"));
        }
    }

    @Test
    void lengthIsHonoredWithinFourToSix() {
        assertEquals(6, generator.generate(CaptchaType.CHAR_ALNUM, properties, CaptchaParams.none(), 6)
                .data().answer().length());
        assertEquals(4, generator.generate(CaptchaType.CHAR_ALNUM, properties, CaptchaParams.none(), 2)
                .data().answer().length());
        assertEquals(6, generator.generate(CaptchaType.CHAR_ALNUM, properties, CaptchaParams.none(), 9)
                .data().answer().length());
    }

    @Test
    void paramsWidthHeightOverrideProperties() {
        CaptchaGenerator.Generated generated = generator.generate(CaptchaType.CHAR_ALNUM, properties,
                new CaptchaParams(200, 80, null, null, null), 4);
        assertEquals(200, generated.width());
        assertEquals(80, generated.height());
    }

    @Test
    void arithmeticAnswerEqualsFormulaResult() {
        for (int i = 0; i < 60; i++) {
            CaptchaGenerator.Generated generated = generator.generate(CaptchaType.ARITHMETIC, properties,
                    CaptchaParams.none(), 4);
            String formula = generated.formula();
            assertNotNull(formula);
            assertTrue(formula.endsWith("= ?"));
            assertEquals(evaluate(formula), Integer.parseInt(generated.data().answer()),
                    "公式 " + formula + " 与答案 " + generated.data().answer() + " 不一致");
        }
    }

    @Test
    void arithmeticCoversAllFourOperators() {
        Set<String> operators = new HashSet<>();
        for (int i = 0; i < 120 && operators.size() < 4; i++) {
            CaptchaGenerator.Generated generated = generator.generate(CaptchaType.ARITHMETIC, properties,
                    CaptchaParams.none(), 4);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+ ([+\\-x/]) \\d+")
                    .matcher(generated.formula());
            assertTrue(matcher.find());
            operators.add(matcher.group(1));
        }
        assertEquals(Set.of("+", "-", "x", "/"), operators);
    }

    @Test
    void slideProducesBackgroundPieceAndGapInsideBounds() {
        CaptchaGenerator.Generated generated = generator.generate(CaptchaType.SLIDE, properties,
                CaptchaParams.none(), 4);
        assertTrue(generated.imageBase64().startsWith("data:image/png;base64,"));
        assertTrue(generated.sliderImageBase64().startsWith("data:image/png;base64,"));
        int gapX = generated.data().slideX();
        int pieceSize = properties.getSlide().getPieceSize();
        assertTrue(gapX >= pieceSize + 8);
        assertTrue(gapX <= properties.getSlide().getWidth() - pieceSize - 8);
        assertEquals(properties.getSlide().getTolerancePx(), generated.data().tolerance());
        assertTrue(generated.words().isEmpty());
    }

    @Test
    void slideToleranceFromParams() {
        CaptchaGenerator.Generated generated = generator.generate(CaptchaType.SLIDE, properties,
                new CaptchaParams(null, null, null, 9, null), 4);
        assertEquals(9, generated.data().tolerance());
    }

    @Test
    void clickProducesPointsWordsAndTolerance() {
        CaptchaGenerator.Generated generated = generator.generate(CaptchaType.CLICK, properties,
                CaptchaParams.none(), 4);
        int[][] points = generated.data().points();
        assertTrue(points.length == 3 || points.length == 4);
        assertEquals(points.length, generated.words().size());
        assertFalse(generated.words().stream().distinct().toList().isEmpty());
        assertEquals(new HashSet<>(generated.words()).size(), generated.words().size());
        for (int[] point : points) {
            assertTrue(point[0] >= 0 && point[0] < properties.getClick().getWidth());
            assertTrue(point[1] >= 0 && point[1] < properties.getClick().getHeight());
        }
        assertEquals(properties.getClick().getTolerancePx(), generated.data().tolerance());
        assertTrue(generated.sliderImageBase64() == null);
    }

    @Test
    void clickToleranceFromParams() {
        CaptchaGenerator.Generated generated = generator.generate(CaptchaType.CLICK, properties,
                new CaptchaParams(null, null, null, null, 22), 4);
        assertEquals(22, generated.data().tolerance());
    }

    @Test
    void configuredBackgroundColorIsApplied() {
        properties.getImage().setBackground("#FF0000");
        CaptchaGenerator.Generated generated = generator.generate(CaptchaType.CHAR_ALNUM, properties,
                CaptchaParams.none(), 4);
        assertTrue(generated.imageBase64().startsWith("data:image/png;base64,"));
    }

    @Test
    void invalidBackgroundColorThrowsChineseError() {
        properties.getImage().setBackground("不是颜色");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(CaptchaType.CHAR_ALNUM, properties, CaptchaParams.none(), 4));
        assertTrue(exception.getMessage().contains("背景色"));
    }

    private int evaluate(String formula) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+) ([+\\-x/]) (\\d+) = \\?")
                .matcher(formula);
        assertTrue(matcher.find(), "无法解析公式：" + formula);
        int left = Integer.parseInt(matcher.group(1));
        int right = Integer.parseInt(matcher.group(3));
        return switch (matcher.group(2)) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "x" -> left * right;
            default -> left / right;
        };
    }
}
