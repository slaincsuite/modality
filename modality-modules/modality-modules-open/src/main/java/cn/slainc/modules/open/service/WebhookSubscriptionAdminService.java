package cn.slainc.modules.open.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.open.dto.WebhookSubscriptionRequest;
import cn.slainc.modules.open.entity.WebhookSubscription;
import cn.slainc.modules.open.store.JdbcWebhookDeliveryStore;
import cn.slainc.modules.open.store.JdbcWebhookSubscriptionStore;
import cn.slainc.utils.string.StringUtils;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.spi.WebhookPublisher;
import cn.slainc.webhook.support.WebhookEventMatcher;
import cn.slainc.webhook.support.WebhookEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WebhookSubscriptionAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookSubscriptionAdminService.class);

    private final JdbcWebhookSubscriptionStore subscriptionStore;

    private final JdbcWebhookDeliveryStore deliveryStore;

    private final WebhookProperties properties;

    private final ObjectProvider<WebhookPublisher> webhookPublisherProvider;

    public WebhookSubscriptionAdminService(JdbcWebhookSubscriptionStore subscriptionStore,
                                           JdbcWebhookDeliveryStore deliveryStore, WebhookProperties properties,
                                           ObjectProvider<WebhookPublisher> webhookPublisherProvider) {
        this.subscriptionStore = subscriptionStore;
        this.deliveryStore = deliveryStore;
        this.properties = properties;
        this.webhookPublisherProvider = webhookPublisherProvider;
    }

    public PageResult<Map<String, Object>> page(String name, Boolean enabled, long current, long size) {
        return subscriptionStore.page(name, enabled, current, size).map(this::toView);
    }

    public Map<String, Object> detail(Long id) {
        return toView(require(id));
    }

    public Map<String, Object> create(WebhookSubscriptionRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "订阅不能为空");
        }
        String name = StringUtils.trimToNull(request.name());
        String url = StringUtils.trimToNull(request.url());
        if (name == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "订阅名称不能为空");
        }
        if (url == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "回调地址不能为空");
        }
        WebhookSubscription insert = new WebhookSubscription();
        insert.setName(name);
        insert.setUrl(url);
        insert.setSecret(StringUtils.trimToNull(request.secret()));
        insert.setEvents(StringUtils.defaultIfBlank(StringUtils.trimToNull(request.events()),
                WebhookEventTypes.WILDCARD));
        insert.setEnabled(request.enabled() == null || request.enabled());
        insert.setMaxRetry(request.maxRetry() == null || request.maxRetry() <= 0
                ? properties.getMaxRetry() : request.maxRetry());
        insert.setTimeoutMillis(request.timeoutMillis() == null || request.timeoutMillis() <= 0
                ? properties.getTimeoutMillis() : request.timeoutMillis());
        subscriptionStore.insert(insert);
        publishEvent(WebhookEventTypes.WEBHOOK_SUBSCRIPTION_CREATED, insert);
        return toView(insert);
    }

    public Map<String, Object> update(Long id, WebhookSubscriptionRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "订阅不能为空");
        }
        WebhookSubscription existing = require(id);
        WebhookSubscription update = new WebhookSubscription();
        update.setId(existing.getId());
        update.setVersion(request.version() == null ? existing.getVersion() : request.version());
        update.setName(StringUtils.defaultIfBlank(StringUtils.trimToNull(request.name()), existing.getName()));
        update.setUrl(StringUtils.defaultIfBlank(StringUtils.trimToNull(request.url()), existing.getUrl()));
        update.setSecret(request.secret() == null ? existing.getSecret()
                : StringUtils.trimToNull(request.secret()));
        update.setEvents(request.events() == null ? existing.getEvents() : request.events());
        update.setEnabled(request.enabled() == null ? existing.getEnabled() : request.enabled());
        update.setMaxRetry(request.maxRetry() == null ? existing.getMaxRetry() : request.maxRetry());
        update.setTimeoutMillis(request.timeoutMillis() == null ? existing.getTimeoutMillis()
                : request.timeoutMillis());
        int rows = subscriptionStore.update(update);
        if (rows <= 0) {
            throw new BizException(ResultCode.CONFLICT, "订阅已被他人修改，请刷新后重试");
        }
        WebhookSubscription updated = subscriptionStore.findById(id);
        publishEvent(WebhookEventTypes.WEBHOOK_SUBSCRIPTION_UPDATED, updated);
        return toView(updated);
    }

    public int remove(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<WebhookSubscription> existing = subscriptionStore.findByIds(ids);
        deliveryStore.deleteBySubscriptionIds(ids);
        int deleted = subscriptionStore.deleteByIds(ids);
        for (WebhookSubscription subscription : existing) {
            publishEvent(WebhookEventTypes.WEBHOOK_SUBSCRIPTION_REMOVED, subscription);
        }
        return deleted;
    }

    public List<WebhookEventTypes.EventType> events() {
        return WebhookEventTypes.all();
    }

    public List<WebhookSubscription> findEnabledByEvent(String eventType) {
        if (StringUtils.isBlank(eventType)) {
            return List.of();
        }
        List<WebhookSubscription> subscriptions = subscriptionStore.findEnabled();
        List<WebhookSubscription> matched = new ArrayList<>();
        for (WebhookSubscription subscription : subscriptions) {
            if (WebhookEventMatcher.matches(subscription.getEvents(), eventType)) {
                matched.add(subscription);
            }
        }
        return matched;
    }

    // 发布订阅变更事件（不含签名密钥明文）；未装配发布器时静默跳过
    private void publishEvent(String eventType, WebhookSubscription subscription) {
        if (subscription == null) {
            return;
        }
        // 惰性解析：WebhookPublisher 依赖本服务，构造期解析会形成循环依赖
        WebhookPublisher webhookPublisher = webhookPublisherProvider == null
                ? null : webhookPublisherProvider.getIfAvailable();
        if (webhookPublisher == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventType);
        payload.put("id", subscription.getId() == null ? null : String.valueOf(subscription.getId()));
        payload.put("name", subscription.getName());
        payload.put("url", subscription.getUrl());
        payload.put("events", subscription.getEvents());
        payload.put("enabled", subscription.getEnabled());
        try {
            webhookPublisher.publish(eventType, payload);
        } catch (RuntimeException e) {
            LOGGER.warn("发布 Webhook 事件 {} 失败：{}", eventType, e.getMessage());
        }
    }

    private WebhookSubscription require(Long id) {
        WebhookSubscription subscription = id == null ? null : subscriptionStore.findById(id);
        if (subscription == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "订阅不存在");
        }
        return subscription;
    }

    private Map<String, Object> toView(WebhookSubscription subscription) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", subscription.getId());
        view.put("name", subscription.getName());
        view.put("url", subscription.getUrl());
        view.put("secretMasked", maskSecret(subscription.getSecret()));
        view.put("events", subscription.getEvents());
        view.put("enabled", subscription.getEnabled());
        view.put("maxRetry", subscription.getMaxRetry());
        view.put("timeoutMillis", subscription.getTimeoutMillis());
        view.put("version", subscription.getVersion());
        view.put("createdAt", subscription.getCreatedAt());
        view.put("updatedAt", subscription.getUpdatedAt());
        return view;
    }

    private static String maskSecret(String secret) {
        return StringUtils.isBlank(secret) ? null : StringUtils.mask(secret, 2, 2);
    }
}
