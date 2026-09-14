package cn.slainc.sso.client;

import cn.dev33.satoken.sso.config.SaSsoClientModel;

import java.util.ArrayList;
import java.util.List;

public class SsoClientRecord {

    public static final String STATUS_ENABLED = "enabled";

    public static final String STATUS_DISABLED = "disabled";

    private Long id;

    private String client;

    private String clientName = "";

    private String secretKey = "";

    private String allowUrl = "";

    private String baseUrl = "";

    private String pushUrl = "/sso/pushC";

    private boolean push = true;

    private boolean slo = true;

    private String status = STATUS_ENABLED;

    private long createdAt;

    private long updatedAt;

    public boolean enabled() {
        return STATUS_ENABLED.equals(status);
    }

    public SaSsoClientModel toModel() {
        SaSsoClientModel model = new SaSsoClientModel();
        model.setClient(client);
        model.setSecretKey(secretKey);
        model.setAllowUrl(allowUrl);
        model.setServerUrl(baseUrl);
        model.setPushUrl(pushUrl);
        model.setIsPush(push);
        model.setIsSlo(slo);
        return model;
    }

    public static List<String> splitAllowUrl(String allowUrl) {
        List<String> result = new ArrayList<>();
        if (allowUrl != null && !allowUrl.isBlank()) {
            for (String part : allowUrl.split("\n")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getAllowUrl() {
        return allowUrl;
    }

    public void setAllowUrl(String allowUrl) {
        this.allowUrl = allowUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPushUrl() {
        return pushUrl;
    }

    public void setPushUrl(String pushUrl) {
        this.pushUrl = pushUrl;
    }

    public boolean isPush() {
        return push;
    }

    public void setPush(boolean push) {
        this.push = push;
    }

    public boolean isSlo() {
        return slo;
    }

    public void setSlo(boolean slo) {
        this.slo = slo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
