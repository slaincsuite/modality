package cn.slainc.captcha;

public record CaptchaVerifyResult(boolean success, String message) {

    public static CaptchaVerifyResult ok() {
        return new CaptchaVerifyResult(true, "验证通过");
    }

    public static CaptchaVerifyResult fail(String message) {
        return new CaptchaVerifyResult(false, message);
    }
}
