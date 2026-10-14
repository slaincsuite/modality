package cn.slainc.modules.open.dto;

public record WebhookSubscriptionRequest(String name, String url, String secret, String events, Boolean enabled,
                                         Integer maxRetry, Long timeoutMillis, Integer version) {
}
