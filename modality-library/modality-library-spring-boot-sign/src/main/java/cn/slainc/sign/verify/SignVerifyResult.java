package cn.slainc.sign.verify;

public record SignVerifyResult(boolean success, String reason, String appId) {

    public static SignVerifyResult ok(String appId) {
        return new SignVerifyResult(true, "验签通过", appId);
    }

    public static SignVerifyResult fail(String appId, String reason) {
        return new SignVerifyResult(false, reason, appId);
    }
}
