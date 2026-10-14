package cn.slainc.modules.open.entity;

public class WebhookSubscription {

    private Long id;

    private String name;

    private String url;

    private String secret;

    private String events;

    private Boolean enabled;

    private Integer maxRetry;

    private Long timeoutMillis;

    private Integer version;

    private Long createdAt;

    private Long updatedAt;

    public WebhookSubscription() {
    }

    public WebhookSubscription(Long id, String name, String url, String secret, String events, Boolean enabled,
                               Integer maxRetry, Long timeoutMillis, Integer version, Long createdAt,
                               Long updatedAt) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.secret = secret;
        this.events = events;
        this.enabled = enabled;
        this.maxRetry = maxRetry;
        this.timeoutMillis = timeoutMillis;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getEvents() {
        return events;
    }

    public void setEvents(String events) {
        this.events = events;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(Integer maxRetry) {
        this.maxRetry = maxRetry;
    }

    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(Long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
