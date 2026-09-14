package cn.slainc.oauth2.client;

import java.util.ArrayList;
import java.util.List;

public class Oauth2ClientRecord {

    public static final String STATUS_ENABLED = "enabled";

    public static final String STATUS_DISABLED = "disabled";

    private Long id;

    private String clientId;

    private String clientSecret;

    private String previousSecret;

    private Long previousSecretExpireAt;

    private String clientName = "";

    private List<String> contractScopes = new ArrayList<>();

    private List<String> allowRedirectUris = new ArrayList<>();

    private List<String> allowGrantTypes = new ArrayList<>();

    private String subjectId = "";

    private long accessTokenTimeout;

    private long refreshTokenTimeout;

    private long clientTokenTimeout;

    private int maxAccessTokenCount;

    private int maxRefreshTokenCount;

    private int maxClientTokenCount;

    private boolean newRefresh = true;

    private boolean autoConfirm = false;

    private List<String> ipWhitelist = new ArrayList<>();

    private long qpsLimit;

    private String status = STATUS_ENABLED;

    private String remark = "";

    private long createdAt;

    private long updatedAt;

    public boolean enabled() {
        return STATUS_ENABLED.equals(status);
    }

    public boolean previousSecretValid(String secret) {
        if (secret == null || previousSecret == null || secret.isBlank()) {
            return false;
        }
        if (previousSecretExpireAt != null && previousSecretExpireAt > 0
                && System.currentTimeMillis() > previousSecretExpireAt) {
            return false;
        }
        return previousSecret.equals(secret);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getPreviousSecret() {
        return previousSecret;
    }

    public void setPreviousSecret(String previousSecret) {
        this.previousSecret = previousSecret;
    }

    public Long getPreviousSecretExpireAt() {
        return previousSecretExpireAt;
    }

    public void setPreviousSecretExpireAt(Long previousSecretExpireAt) {
        this.previousSecretExpireAt = previousSecretExpireAt;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public List<String> getContractScopes() {
        return contractScopes;
    }

    public void setContractScopes(List<String> contractScopes) {
        this.contractScopes = contractScopes;
    }

    public List<String> getAllowRedirectUris() {
        return allowRedirectUris;
    }

    public void setAllowRedirectUris(List<String> allowRedirectUris) {
        this.allowRedirectUris = allowRedirectUris;
    }

    public List<String> getAllowGrantTypes() {
        return allowGrantTypes;
    }

    public void setAllowGrantTypes(List<String> allowGrantTypes) {
        this.allowGrantTypes = allowGrantTypes;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public long getAccessTokenTimeout() {
        return accessTokenTimeout;
    }

    public void setAccessTokenTimeout(long accessTokenTimeout) {
        this.accessTokenTimeout = accessTokenTimeout;
    }

    public long getRefreshTokenTimeout() {
        return refreshTokenTimeout;
    }

    public void setRefreshTokenTimeout(long refreshTokenTimeout) {
        this.refreshTokenTimeout = refreshTokenTimeout;
    }

    public long getClientTokenTimeout() {
        return clientTokenTimeout;
    }

    public void setClientTokenTimeout(long clientTokenTimeout) {
        this.clientTokenTimeout = clientTokenTimeout;
    }

    public int getMaxAccessTokenCount() {
        return maxAccessTokenCount;
    }

    public void setMaxAccessTokenCount(int maxAccessTokenCount) {
        this.maxAccessTokenCount = maxAccessTokenCount;
    }

    public int getMaxRefreshTokenCount() {
        return maxRefreshTokenCount;
    }

    public void setMaxRefreshTokenCount(int maxRefreshTokenCount) {
        this.maxRefreshTokenCount = maxRefreshTokenCount;
    }

    public int getMaxClientTokenCount() {
        return maxClientTokenCount;
    }

    public void setMaxClientTokenCount(int maxClientTokenCount) {
        this.maxClientTokenCount = maxClientTokenCount;
    }

    public boolean isNewRefresh() {
        return newRefresh;
    }

    public void setNewRefresh(boolean newRefresh) {
        this.newRefresh = newRefresh;
    }

    public boolean isAutoConfirm() {
        return autoConfirm;
    }

    public void setAutoConfirm(boolean autoConfirm) {
        this.autoConfirm = autoConfirm;
    }

    public List<String> getIpWhitelist() {
        return ipWhitelist;
    }

    public void setIpWhitelist(List<String> ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    public long getQpsLimit() {
        return qpsLimit;
    }

    public void setQpsLimit(long qpsLimit) {
        this.qpsLimit = qpsLimit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
