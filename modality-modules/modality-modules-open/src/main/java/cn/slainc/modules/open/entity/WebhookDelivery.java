package cn.slainc.modules.open.entity;

public class WebhookDelivery {

    private Long id;

    private Long subscriptionId;

    private String eventType;

    private String payload;

    private String status;

    private Integer attempt;

    private Integer httpStatus;

    private String responseBody;

    private String errorMessage;

    private Long nextRetryAt;

    private Long costMillis;

    private Long createdAt;

    private Long updatedAt;

    public WebhookDelivery() {
    }

    public WebhookDelivery(Long id, Long subscriptionId, String eventType, String payload, String status,
                           Integer attempt, Integer httpStatus, String responseBody, String errorMessage,
                           Long nextRetryAt, Long costMillis, Long createdAt, Long updatedAt) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.attempt = attempt;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.errorMessage = errorMessage;
        this.nextRetryAt = nextRetryAt;
        this.costMillis = costMillis;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Long nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Long getCostMillis() {
        return costMillis;
    }

    public void setCostMillis(Long costMillis) {
        this.costMillis = costMillis;
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
