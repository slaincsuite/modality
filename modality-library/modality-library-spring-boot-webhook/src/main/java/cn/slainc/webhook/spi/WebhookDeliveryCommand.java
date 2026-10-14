package cn.slainc.webhook.spi;

public record WebhookDeliveryCommand(Long subscriptionId, String eventType, String payload, String url, String secret,
                                     int maxRetry, long timeoutMillis, Long tenantId) {
}
