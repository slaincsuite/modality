package cn.slainc.captcha;

import java.util.List;

public record CaptchaInput(String code, Integer slideX, List<ClickPoint> points) {

    public record ClickPoint(int x, int y) {
    }

    public static CaptchaInput ofCode(String code) {
        return new CaptchaInput(code, null, null);
    }

    public static CaptchaInput ofSlide(int slideX) {
        return new CaptchaInput(null, slideX, null);
    }

    public static CaptchaInput ofClick(List<ClickPoint> points) {
        return new CaptchaInput(null, null, points);
    }

    public boolean isEmpty() {
        boolean hasCode = code != null && !code.isBlank();
        return !hasCode && slideX == null && (points == null || points.isEmpty());
    }
}
