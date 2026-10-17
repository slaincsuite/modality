package cn.slainc.modules.auth.third;

public class FeishuAppConfig {

    private Long id;

    private String platform;

    private String appId;

    private String appSecretCipher;

    private String verificationTokenCipher;

    private String encryptKeyCipher;

    private String callbackUrl;

    private Boolean enabled;

    private Long createTime;

    private Long updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecretCipher() {
        return appSecretCipher;
    }

    public void setAppSecretCipher(String appSecretCipher) {
        this.appSecretCipher = appSecretCipher;
    }

    public String getVerificationTokenCipher() {
        return verificationTokenCipher;
    }

    public void setVerificationTokenCipher(String verificationTokenCipher) {
        this.verificationTokenCipher = verificationTokenCipher;
    }

    public String getEncryptKeyCipher() {
        return encryptKeyCipher;
    }

    public void setEncryptKeyCipher(String encryptKeyCipher) {
        this.encryptKeyCipher = encryptKeyCipher;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
}
