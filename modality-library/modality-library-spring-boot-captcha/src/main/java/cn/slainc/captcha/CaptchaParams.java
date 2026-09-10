package cn.slainc.captcha;

public record CaptchaParams(Integer width, Integer height, Integer length,
                            Integer slideTolerancePx, Integer clickTolerancePx) {

    public static CaptchaParams none() {
        return new CaptchaParams(null, null, null, null, null);
    }
}
