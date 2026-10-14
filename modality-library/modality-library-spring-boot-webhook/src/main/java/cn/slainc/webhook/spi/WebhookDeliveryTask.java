package cn.slainc.webhook.spi;

public record WebhookDeliveryTask(Long deliveryId, Long subscriptionId, String eventType, String payload, String url,
                                  String secret, int attempt, int maxRetry, long timeoutMillis) {
}
