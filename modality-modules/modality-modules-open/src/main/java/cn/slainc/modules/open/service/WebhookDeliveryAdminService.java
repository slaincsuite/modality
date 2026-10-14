package cn.slainc.modules.open.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.open.entity.WebhookDelivery;
import cn.slainc.modules.open.entity.WebhookSubscription;
import cn.slainc.modules.open.store.JdbcWebhookDeliveryStore;
import cn.slainc.modules.open.store.JdbcWebhookSubscriptionStore;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.enums.WebhookDeliveryStatus;
import cn.slainc.webhook.spi.WebhookDeliveryCommand;
import cn.slainc.webhook.spi.WebhookDeliveryPort;
import cn.slainc.webhook.spi.WebhookDeliveryTask;
import cn.slainc.webhook.support.WebhookDeliveryDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WebhookDeliveryAdminService implements WebhookDeliveryPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookDeliveryAdminService.class);

    private static final String ORPHAN_MESSAGE = "订阅不存在，无法投递";

    private final JdbcWebhookDeliveryStore deliveryStore;

    private final JdbcWebhookSubscriptionStore subscriptionStore;

    private final WebhookDeliveryDispatcher dispatcher;

    private final WebhookProperties properties;

    private final Executor executor;

    public WebhookDeliveryAdminService(JdbcWebhookDeliveryStore deliveryStore,
                                       JdbcWebhookSubscriptionStore subscriptionStore,
                                       WebhookDeliveryDispatcher dispatcher, WebhookProperties properties,
                                       Executor executor) {
        this.deliveryStore = deliveryStore;
        this.subscriptionStore = subscriptionStore;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.executor = executor;
    }

    public void deliverAsync(Long deliveryId) {
        if (deliveryId == null) {
            return;
        }
        Runnable task = () -> {
            try {
                deliver(deliveryId);
            } catch (RuntimeException e) {
                LOGGER.error("投递 Webhook {} 失败：{}", deliveryId, e.getMessage(), e);
            }
        };
        if (executor == null) {
            task.run();
            return;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Webhook 投递 {} 被执行器拒绝，等待重投调度接管", deliveryId);
        }
    }

    public WebhookDelivery deliver(Long deliveryId) {
        WebhookDelivery delivery = deliveryId == null ? null : deliveryStore.findById(deliveryId);
        if (delivery == null) {
            LOGGER.warn("Webhook 投递记录 {} 不存在", deliveryId);
            return null;
        }
        if (WebhookDeliveryStatus.of(delivery.getStatus()).terminal()) {
            LOGGER.debug("Webhook 投递 {} 已终态（{}），跳过", deliveryId, delivery.getStatus());
            return delivery;
        }
        WebhookSubscription subscription = subscriptionStore.findById(delivery.getSubscriptionId());
        if (subscription == null) {
            deliveryStore.markFailure(delivery.getId(), attemptOf(delivery),
                    WebhookDeliveryStatus.EXHAUSTED.name(), 0, null, ORPHAN_MESSAGE, null, 0L);
            return deliveryStore.findById(deliveryId);
        }
        dispatcher.deliver(taskOf(delivery, subscription));
        return deliveryStore.findById(deliveryId);
    }

    public boolean retry(Long deliveryId) {
        WebhookDelivery delivery = deliveryId == null ? null : deliveryStore.findById(deliveryId);
        if (delivery == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "投递记录不存在");
        }
        if (WebhookDeliveryStatus.SUCCESS == WebhookDeliveryStatus.of(delivery.getStatus())) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "投递已成功，无需重试");
        }
        deliveryStore.resetForRetry(deliveryId);
        deliverAsync(deliveryId);
        return true;
    }

    public PageResult<Map<String, Object>> page(Long subscriptionId, String eventType, String status, long current,
                                                long size) {
        return deliveryStore.page(subscriptionId, eventType, status, current, size).map(this::toView);
    }

    @Override
    public Long createPending(WebhookDeliveryCommand command) {
        if (command == null) {
            return null;
        }
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setSubscriptionId(command.subscriptionId());
        delivery.setEventType(command.eventType());
        delivery.setPayload(command.payload());
        delivery.setStatus(WebhookDeliveryStatus.PENDING.name());
        delivery.setAttempt(0);
        return deliveryStore.insertPending(delivery);
    }

    @Override
    public List<WebhookDeliveryTask> findDue(int limit) {
        List<WebhookDelivery> due = deliveryStore.findDue(Math.max(limit, 1));
        if (due.isEmpty()) {
            return List.of();
        }
        Map<Long, WebhookSubscription> subscriptions = subscriptions(due);
        List<WebhookDeliveryTask> tasks = new ArrayList<>(due.size());
        for (WebhookDelivery delivery : due) {
            WebhookSubscription subscription = subscriptions.get(delivery.getSubscriptionId());
            if (subscription == null) {
                deliveryStore.markFailure(delivery.getId(), attemptOf(delivery),
                        WebhookDeliveryStatus.EXHAUSTED.name(), 0, null, ORPHAN_MESSAGE, null, 0L);
                continue;
            }
            tasks.add(taskOf(delivery, subscription));
        }
        return tasks;
    }

    @Override
    public void markSuccess(Long id, int httpStatus, String responseBody, long costMillis) {
        deliveryStore.markSuccess(id, httpStatus, responseBody, costMillis);
    }

    @Override
    public void markFailure(Long id, int attempt, int httpStatus, String responseBody, String errorMessage,
                            LocalDateTime nextRetryAt, boolean exhausted, long costMillis) {
        deliveryStore.markFailure(id, attempt,
                exhausted ? WebhookDeliveryStatus.EXHAUSTED.name() : WebhookDeliveryStatus.FAILED.name(),
                httpStatus, responseBody, errorMessage, nextRetryAt, costMillis);
    }

    private WebhookDeliveryTask taskOf(WebhookDelivery delivery, WebhookSubscription subscription) {
        return new WebhookDeliveryTask(delivery.getId(), delivery.getSubscriptionId(), delivery.getEventType(),
                delivery.getPayload(), subscription.getUrl(), subscription.getSecret(), attemptOf(delivery),
                maxRetryOf(subscription), timeoutOf(subscription));
    }

    private Map<Long, WebhookSubscription> subscriptions(List<WebhookDelivery> deliveries) {
        List<Long> ids = deliveries.stream().map(WebhookDelivery::getSubscriptionId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<WebhookSubscription> found = subscriptionStore.findByIds(ids);
        if (found.isEmpty()) {
            return Map.of();
        }
        return found.stream().collect(Collectors.toMap(WebhookSubscription::getId, Function.identity(),
                (left, right) -> left));
    }

    private static int attemptOf(WebhookDelivery delivery) {
        return delivery.getAttempt() == null ? 0 : delivery.getAttempt();
    }

    private long timeoutOf(WebhookSubscription subscription) {
        Long timeout = subscription.getTimeoutMillis();
        if (timeout != null && timeout > 0) {
            return timeout;
        }
        return properties.getTimeoutMillis();
    }

    private int maxRetryOf(WebhookSubscription subscription) {
        Integer maxRetry = subscription.getMaxRetry();
        if (maxRetry != null && maxRetry > 0) {
            return maxRetry;
        }
        return Math.max(properties.getMaxRetry(), 1);
    }

    private Map<String, Object> toView(WebhookDelivery delivery) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", delivery.getId());
        view.put("subscriptionId", delivery.getSubscriptionId());
        view.put("eventType", delivery.getEventType());
        view.put("payload", delivery.getPayload());
        view.put("status", delivery.getStatus());
        view.put("attempt", delivery.getAttempt());
        view.put("httpStatus", delivery.getHttpStatus());
        view.put("responseBody", delivery.getResponseBody());
        view.put("errorMessage", delivery.getErrorMessage());
        view.put("nextRetryAt", delivery.getNextRetryAt());
        view.put("costMillis", delivery.getCostMillis());
        view.put("createdAt", delivery.getCreatedAt());
        view.put("updatedAt", delivery.getUpdatedAt());
        return view;
    }
}
