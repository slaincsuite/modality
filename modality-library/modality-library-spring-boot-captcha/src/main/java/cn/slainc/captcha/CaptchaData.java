package cn.slainc.captcha;

public record CaptchaData(
        CaptchaType type,
        String answer,
        Integer slideX,
        int[][] points,
        int tolerance,
        int width,
        int height,
        long createdAt) {
}
