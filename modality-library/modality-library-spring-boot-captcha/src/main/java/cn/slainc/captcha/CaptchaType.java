package cn.slainc.captcha;

import java.util.Locale;

public enum CaptchaType {

    CHAR_NUMBER("数字字符"),
    CHAR_ALPHA("字母字符"),
    CHAR_ALNUM("数字字母混合"),
    CHINESE("中文字符"),
    ARITHMETIC("算术运算"),
    SLIDE("滑动拼图"),
    CLICK("文字点选");

    private final String description;

    CaptchaType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean behaviorBased() {
        return this == SLIDE || this == CLICK;
    }

    public static CaptchaType of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CaptchaType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
