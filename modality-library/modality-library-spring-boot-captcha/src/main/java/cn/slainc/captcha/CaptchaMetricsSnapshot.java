package cn.slainc.captcha;

public record CaptchaMetricsSnapshot(long generated, long verifySuccess, long verifyFailure, long locked) {
}
