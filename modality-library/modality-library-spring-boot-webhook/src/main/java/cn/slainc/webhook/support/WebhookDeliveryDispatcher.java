package cn.slainc.webhook.support;

import cn.slainc.utils.string.StringUtils;
import cn.slainc.webhook.spi.WebhookSender;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.sender.WebhookSendRequest;
import cn.slainc.webhook.sender.WebhookSendResult;
import cn.slainc.webhook.spi.WebhookDeliveryPort;
import cn.slainc.webhook.spi.WebhookDeliveryTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class WebhookDeliveryDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookDeliveryDispatcher.class);

    private static final int MAX_ERROR_LENGTH = 1024;

    private final WebhookSender sender;

    private final WebhookProperties properties;

    private final WebhookDeliveryPort port;

    private final ObjectProvider<WebhookDeliveryPort> portProvider;

    public WebhookDeliveryDispatcher(WebhookSender sender, WebhookProperties properties, WebhookDeliveryPort port) {
        this(sender, properties, port, null);
    }

    public WebhookDeliveryDispatcher(WebhookSender sender, WebhookProperties properties,
                                     ObjectProvider<WebhookDeliveryPort> portProvider) {
        this(sender, properties, null, portProvider);
    }

    private WebhookDeliveryDispatcher(WebhookSender sender, WebhookProperties properties, WebhookDeliveryPort port,
                                      ObjectProvider<WebhookDeliveryPort> portProvider) {
        this.sender = sender;
        this.properties = properties;
        this.port = port;
        this.portProvider = portProvider;
    }

    public void deliver(WebhookDeliveryTask task) {
        if (task == null || task.deliveryId() == null) {
            LOGGER.debug("跳过空的 Webhook 投递任务");
            return;
        }
        WebhookDeliveryPort deliveryPort = resolvePort();
        if (deliveryPort == null) {
            LOGGER.warn("未注册 WebhookDeliveryPort，跳过 Webhook 投递 {}", task.deliveryId());
            return;
        }
        int attempt = Math.max(task.attempt(), 0) + 1;
        long startedAt = System.currentTimeMillis();
        WebhookSendResult result = send(task);
        long costMillis = System.currentTimeMillis() - startedAt;
        if (result.success()) {
            deliveryPort.markSuccess(task.deliveryId(), result.httpStatus(), result.responseBody(), costMillis);
            LOGGER.debug("Webhook 投递 {} 成功，status={} attempt={}", task.deliveryId(), result.httpStatus(),
                    attempt);
            return;
        }
        int maxRetry = Math.max(task.maxRetry(), 1);
        String errorMessage = truncate(result.errorMessage());
        int httpStatus = result.httpStatus() == null ? 0 : result.httpStatus();
        if (attempt < maxRetry) {
            LocalDateTime nextRetryAt = LocalDateTime.now().plus(WebhookBackoff.nextDelay(attempt,
                    initialBackoff(), maxBackoff()));
            deliveryPort.markFailure(task.deliveryId(), attempt, httpStatus, result.responseBody(), errorMessage,
                    nextRetryAt, false, costMillis);
            LOGGER.debug("Webhook 投递 {} 第 {}/{} 次失败，下次重投时间 {}", task.deliveryId(), attempt, maxRetry,
                    nextRetryAt);
        } else {
            deliveryPort.markFailure(task.deliveryId(), attempt, httpStatus, result.responseBody(), errorMessage,
                    null, true, costMillis);
            LOGGER.debug("Webhook 投递 {} 重试 {} 次后放弃", task.deliveryId(), attempt);
        }
    }

    public int retryDue(int batchSize) {
        WebhookDeliveryPort deliveryPort = resolvePort();
        if (deliveryPort == null) {
            LOGGER.debug("未注册 WebhookDeliveryPort，跳过本轮 Webhook 重投扫描");
            return 0;
        }
        List<WebhookDeliveryTask> due = deliveryPort.findDue(Math.max(batchSize, 1));
        if (due == null || due.isEmpty()) {
            return 0;
        }
        int retried = 0;
        for (WebhookDeliveryTask task : due) {
            deliver(task);
            retried++;
        }
        return retried;
    }

    private WebhookSendResult send(WebhookDeliveryTask task) {
        if (sender == null) {
            return WebhookSendResult.failed("WebhookSender 未装配，无法投递");
        }
        WebhookSendRequest request = new WebhookSendRequest(task.deliveryId(), task.eventType(), task.url(),
                task.secret(), task.payload(), timeoutOf(task));
        try {
            return sender.send(request);
        } catch (RuntimeException e) {
            LOGGER.warn("Webhook 投递 {} 的发送器抛出异常：{}", task.deliveryId(), e.getMessage());
            return WebhookSendResult.failed(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private WebhookDeliveryPort resolvePort() {
        if (port != null) {
            return port;
        }
        if (portProvider == null) {
            return null;
        }
        try {
            return portProvider.getIfAvailable();
        } catch (RuntimeException e) {
            LOGGER.warn("解析 WebhookDeliveryPort 失败：{}", e.getMessage());
            return null;
        }
    }

    private long timeoutOf(WebhookDeliveryTask task) {
        if (task.timeoutMillis() > 0) {
            return task.timeoutMillis();
        }
        return properties == null ? 0L : properties.getTimeoutMillis();
    }

    private Duration initialBackoff() {
        return properties == null ? null : properties.getInitialBackoff();
    }

    private Duration maxBackoff() {
        return properties == null ? null : properties.getMaxBackoff();
    }

    private String truncate(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
