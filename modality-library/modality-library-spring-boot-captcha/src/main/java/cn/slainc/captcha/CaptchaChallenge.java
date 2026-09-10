package cn.slainc.captcha;

import java.util.List;

public record CaptchaChallenge(
        String id,
        String channel,
        CaptchaType type,
        String imageBase64,
        String sliderImageBase64,
        List<String> words,
        int width,
        int height,
        long ttlSeconds) {
}
