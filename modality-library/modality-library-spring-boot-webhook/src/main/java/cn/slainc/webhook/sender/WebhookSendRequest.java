package cn.slainc.webhook.sender;

public record WebhookSendRequest(Long deliveryId, String eventType, String url, String secret, String body,
                                 long timeoutMillis) {
}
