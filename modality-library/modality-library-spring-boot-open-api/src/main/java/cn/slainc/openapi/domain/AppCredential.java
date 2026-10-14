package cn.slainc.openapi.domain;

public class AppCredential {

    public enum Type {
        OAUTH2,
        API_KEY,
        GENERATED
    }

    public enum Status {
        ENABLED,
        DISABLED
    }

    private String appId;

    private String clientId;

    private String clientSecret;

    private Type type = Type.GENERATED;

    private Status status = Status.ENABLED;

    private Long previousSecretExpireAt;

    private long issuedAt;

    private Long rotatedAt;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getPreviousSecretExpireAt() {
        return previousSecretExpireAt;
    }

    public void setPreviousSecretExpireAt(Long previousSecretExpireAt) {
        this.previousSecretExpireAt = previousSecretExpireAt;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(long issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Long getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(Long rotatedAt) {
        this.rotatedAt = rotatedAt;
    }
}
