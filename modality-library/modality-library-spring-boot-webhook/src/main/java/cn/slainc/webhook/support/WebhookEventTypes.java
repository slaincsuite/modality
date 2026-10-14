package cn.slainc.webhook.support;

import java.util.List;

public final class WebhookEventTypes {

    public static final String WILDCARD = "*";

    public static final String API_KEY_CREATED = "open.apikey.created";

    public static final String API_KEY_UPDATED = "open.apikey.updated";

    public static final String API_KEY_REMOVED = "open.apikey.removed";

    public static final String API_KEY_ROTATED = "open.apikey.rotated";

    public static final String API_KEY_STATUS_CHANGED = "open.apikey.status-changed";

    public static final String WEBHOOK_SUBSCRIPTION_CREATED = "open.webhook.subscription-created";

    public static final String WEBHOOK_SUBSCRIPTION_UPDATED = "open.webhook.subscription-updated";

    public static final String WEBHOOK_SUBSCRIPTION_REMOVED = "open.webhook.subscription-removed";

    private static final List<EventType> ALL = List.of(
            new EventType(API_KEY_CREATED, "API Key 创建"),
            new EventType(API_KEY_UPDATED, "API Key 修改"),
            new EventType(API_KEY_REMOVED, "API Key 删除"),
            new EventType(API_KEY_ROTATED, "API Key 密钥轮换"),
            new EventType(API_KEY_STATUS_CHANGED, "API Key 启停"),
            new EventType(WEBHOOK_SUBSCRIPTION_CREATED, "Webhook 订阅创建"),
            new EventType(WEBHOOK_SUBSCRIPTION_UPDATED, "Webhook 订阅修改"),
            new EventType(WEBHOOK_SUBSCRIPTION_REMOVED, "Webhook 订阅删除"));

    private WebhookEventTypes() {
    }

    public static List<EventType> all() {
        return ALL;
    }

    public record EventType(String name, String description) {
    }
}
