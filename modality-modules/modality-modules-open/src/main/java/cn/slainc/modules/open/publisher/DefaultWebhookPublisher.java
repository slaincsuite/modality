package cn.slainc.modules.open.publisher;

import cn.slainc.modules.open.entity.WebhookSubscription;
import cn.slainc.modules.open.service.WebhookDeliveryAdminService;
import cn.slainc.modules.open.service.WebhookSubscriptionAdminService;
import cn.slainc.utils.string.StringUtils;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.spi.WebhookDeliveryCommand;
import cn.slainc.webhook.spi.WebhookPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

public class DefaultWebhookPublisher implements WebhookPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWebhookPublisher.class);

    private final WebhookSubscriptionAdminService subscriptionService;

    private final WebhookDeliveryAdminService deliveryService;

    private final WebhookProperties properties;

    private final JsonMapper jsonMapper;

    public DefaultWebhookPublisher(WebhookSubscriptionAdminService subscriptionService,
                                   WebhookDeliveryAdminService deliveryService, WebhookProperties properties,
                                   JsonMapper jsonMapper) {
        this.subscriptionService = subscriptionService;
        this.deliveryService = deliveryService;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publish(String eventType, Object payload) {
        if (StringUtils.isBlank(eventType) || !properties.isEnabled()) {
            return;
        }
        String body = serialize(eventType, payload);
        afterCommit(() -> dispatch(eventType.trim(), body));
    }

    private void dispatch(String eventType, String body) {
        List<WebhookSubscription> subscriptions = subscriptionService.findEnabledByEvent(eventType);
        for (WebhookSubscription subscription : subscriptions) {
            WebhookDeliveryCommand command = new WebhookDeliveryCommand(subscription.getId(), eventType, body,
                    subscription.getUrl(), subscription.getSecret(),
                    subscription.getMaxRetry() == null ? 0 : subscription.getMaxRetry(),
                    subscription.getTimeoutMillis() == null ? 0L : subscription.getTimeoutMillis(),
                    null);
            deliveryService.deliverAsync(deliveryService.createPending(command));
        }
    }

    // 事务内先落库，提交后再派发，避免订阅方读到未提交数据
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String serialize(String eventType, Object payload) {
        if (payload == null) {
            return "{}";
        }
        if (payload instanceof CharSequence sequence) {
            return sequence.toString();
        }
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (RuntimeException e) {
            LOGGER.warn("序列化 Webhook 事件 {} 的载荷失败：{}", eventType, e.getMessage());
            return "{}";
        }
    }
}
