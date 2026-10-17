package cn.slainc.modules.auth.dto;

public record FeishuConfigRequest(String appId, String appSecret, String verificationToken, String encryptKey,
                                  String callback) {
}
