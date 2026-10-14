package cn.slainc.modules.open.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.open.entity.WebhookDelivery;
import cn.slainc.modules.open.entity.WebhookSubscription;
import cn.slainc.modules.open.store.JdbcWebhookDeliveryStore;
import cn.slainc.modules.open.store.JdbcWebhookSubscriptionStore;
import cn.slainc.webhook.api.WebhookSender;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.enums.WebhookDeliveryStatus;
import cn.slainc.webhook.sender.WebhookSendRequest;
import cn.slainc.webhook.sender.WebhookSendResult;
import cn.slainc.webhook.spi.WebhookDeliveryCommand;
import cn.slainc.webhook.spi.WebhookDeliveryTask;
import cn.slainc.webhook.support.WebhookDeliveryDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookDeliveryAdminServiceTest {

    private final WebhookProperties properties = new WebhookProperties();

    private final FakeWebhookSender sender = new FakeWebhookSender();

    private JdbcTemplate jdbcTemplate;

    private JdbcWebhookSubscriptionStore subscriptionStore;

    private JdbcWebhookDeliveryStore deliveryStore;

    private WebhookDeliveryAdminService service;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:webhook-delivery-" + UUID.randomUUID().toString().replace("-", "")
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new cn.slainc.core.jdbc.ClasspathSchemaInitializer(dataSource, "open-schema.sql").init();
        jdbcTemplate = new JdbcTemplate(dataSource);
        subscriptionStore = new JdbcWebhookSubscriptionStore(jdbcTemplate);
        deliveryStore = new JdbcWebhookDeliveryStore(jdbcTemplate);
        service = new WebhookDeliveryAdminService(deliveryStore, subscriptionStore,
                new WebhookDeliveryDispatcher(sender, properties, delegatingPort()), properties,
                Runnable::run);
    }

    // 构造期 service 尚未赋值，用委托端口在调用时再解析
    private cn.slainc.webhook.spi.WebhookDeliveryPort delegatingPort() {
        return new cn.slainc.webhook.spi.WebhookDeliveryPort() {

            @Override
            public Long createPending(WebhookDeliveryCommand command) {
                return service.createPending(command);
            }

            @Override
            public List<WebhookDeliveryTask> findDue(int limit) {
                return service.findDue(limit);
            }

            @Override
            public void markSuccess(Long id, int httpStatus, String responseBody, long costMillis) {
                service.markSuccess(id, httpStatus, responseBody, costMillis);
            }

            @Override
            public void markFailure(Long id, int attempt, int httpStatus, String responseBody,
                                    String errorMessage, java.time.LocalDateTime nextRetryAt, boolean exhausted,
                                    long costMillis) {
                service.markFailure(id, attempt, httpStatus, responseBody, errorMessage, nextRetryAt, exhausted,
                        costMillis);
            }
        };
    }

    @Test
    void shouldMarkSuccessOnTheFirstAttempt() {
        long id = seedPendingDelivery(0, 0L);
        seedSubscription(3, 5000L);
        sender.enqueue(WebhookSendResult.delivered(200, "ok"));

        WebhookDelivery delivered = service.deliver(id);

        assertThat(sender.calls()).isEqualTo(1);
        assertThat(sender.lastRequest().secret()).isEqualTo("whsec");
        assertThat(sender.lastRequest().timeoutMillis()).isEqualTo(5000L);
        assertThat(delivered.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCESS.name());
        assertThat(delivered.getHttpStatus()).isEqualTo(200);
        assertThat(delivered.getResponseBody()).isEqualTo("ok");
        assertThat(delivered.getErrorMessage()).isNull();
        assertThat(delivered.getNextRetryAt()).isNull();
    }

    @Test
    void shouldBackOffExponentiallyAndFinallySucceed() {
        long id = seedPendingDelivery(0, 0L);
        seedSubscription(3, 5000L);
        sender.enqueue(WebhookSendResult.failed("connection refused"),
                WebhookSendResult.rejected(500, "boom", "HTTP 500"),
                WebhookSendResult.delivered(200, "ok"));

        WebhookDelivery first = service.deliver(id);
        WebhookDelivery second = service.deliver(id);
        WebhookDelivery third = service.deliver(id);

        assertThat(first.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED.name());
        assertThat(first.getAttempt()).isEqualTo(1);
        assertThat(first.getErrorMessage()).isEqualTo("connection refused");
        assertThat(first.getNextRetryAt()).isNotNull();
        assertThat(second.getAttempt()).isEqualTo(2);
        assertThat(second.getHttpStatus()).isEqualTo(500);
        assertThat(second.getNextRetryAt()).isGreaterThan(first.getNextRetryAt());
        assertThat(third.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCESS.name());
    }

    @Test
    void shouldExhaustTheDeliveryWhenTheRetriesRunOut() {
        long id = seedPendingDelivery(0, 0L);
        seedSubscription(2, 5000L);
        sender.enqueue(WebhookSendResult.failed("first"), WebhookSendResult.failed("second"));

        service.deliver(id);
        WebhookDelivery exhausted = service.deliver(id);

        assertThat(exhausted.getStatus()).isEqualTo(WebhookDeliveryStatus.EXHAUSTED.name());
        assertThat(exhausted.getAttempt()).isEqualTo(2);
        assertThat(exhausted.getErrorMessage()).isEqualTo("second");
        assertThat(exhausted.getNextRetryAt()).isNull();
    }

    @Test
    void shouldSkipTerminalDeliveries() {
        long id = seedPendingDelivery(0, 0L);
        jdbcTemplate.update("UPDATE t_webhook_delivery SET status = 'SUCCESS' WHERE id = ?", id);

        WebhookDelivery delivery = service.deliver(id);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCESS.name());
        assertThat(sender.calls()).isZero();
    }

    @Test
    void shouldExhaustWhenTheSubscriptionIsGone() {
        long id = seedPendingDelivery(0, 0L);

        WebhookDelivery result = service.deliver(id);

        assertThat(result.getStatus()).isEqualTo(WebhookDeliveryStatus.EXHAUSTED.name());
        assertThat(result.getErrorMessage()).contains("订阅不存在");
        assertThat(sender.calls()).isZero();
    }

    @Test
    void shouldIgnoreAnUnknownDelivery() {
        assertThat(service.deliver(9L)).isNull();
        assertThat(service.deliver(null)).isNull();
        assertThat(sender.calls()).isZero();
    }

    @Test
    void shouldCreatePendingDeliveries() {
        Long id = service.createPending(new WebhookDeliveryCommand(5L, "open.apikey.created",
                "{\"id\":1}", "https://example.com/hook", "whsec", 3, 5000L, null));

        assertThat(id).isNotNull();
        WebhookDelivery inserted = deliveryStore.findById(id);
        assertThat(inserted.getSubscriptionId()).isEqualTo(5L);
        assertThat(inserted.getEventType()).isEqualTo("open.apikey.created");
        assertThat(inserted.getPayload()).isEqualTo("{\"id\":1}");
        assertThat(inserted.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING.name());
        assertThat(inserted.getAttempt()).isZero();
        assertThat(inserted.getNextRetryAt()).isNotNull();
        assertThat(service.createPending(null)).isNull();
    }

    @Test
    void shouldScanDueDeliveriesIntoDeliveryTasks() {
        seedPendingDelivery(1, 0L);
        seedSubscription(3, 5000L);

        List<WebhookDeliveryTask> due = service.findDue(20);

        assertThat(due).hasSize(1);
        WebhookDeliveryTask task = due.get(0);
        assertThat(task.subscriptionId()).isEqualTo(5L);
        assertThat(task.url()).isEqualTo("https://example.com/hook");
        assertThat(task.secret()).isEqualTo("whsec");
        assertThat(task.attempt()).isEqualTo(1);
        assertThat(task.maxRetry()).isEqualTo(3);
        assertThat(task.timeoutMillis()).isEqualTo(5000L);
    }

    @Test
    void shouldFallBackToThePlatformDefaultsWhenTheSubscriptionHasNoOverrides() {
        seedPendingDelivery(0, 0L);
        seedSubscription(null, 0L);

        WebhookDeliveryTask task = service.findDue(20).get(0);

        assertThat(task.maxRetry()).isEqualTo(properties.getMaxRetry());
        assertThat(task.timeoutMillis()).isEqualTo(properties.getTimeoutMillis());
    }

    @Test
    void shouldExhaustDueDeliveriesWhoseSubscriptionIsGone() {
        long id = seedPendingDelivery(1, 0L);

        assertThat(service.findDue(20)).isEmpty();
        assertThat(deliveryStore.findById(id).getStatus())
                .isEqualTo(WebhookDeliveryStatus.EXHAUSTED.name());
    }

    @Test
    void shouldDeliverScannedTasksThroughTheDispatcher() {
        long id = seedPendingDelivery(0, 0L);
        seedSubscription(3, 5000L);
        sender.enqueue(WebhookSendResult.delivered(204, null));
        WebhookDeliveryDispatcher dispatcher = new WebhookDeliveryDispatcher(sender, properties,
                service);

        assertThat(dispatcher.retryDue(20)).isEqualTo(1);

        assertThat(sender.calls()).isEqualTo(1);
        assertThat(deliveryStore.findById(id).getStatus())
                .isEqualTo(WebhookDeliveryStatus.SUCCESS.name());
    }

    @Test
    void shouldResetAndRedeliverOnAManualRetry() {
        long id = seedPendingDelivery(2, System.currentTimeMillis() + 3600_000L);
        jdbcTemplate.update("UPDATE t_webhook_delivery SET status = 'EXHAUSTED' WHERE id = ?", id);
        seedSubscription(3, 5000L);
        sender.enqueue(WebhookSendResult.delivered(200, "ok"));

        assertThat(service.retry(id)).isTrue();

        assertThat(sender.calls()).isEqualTo(1);
        assertThat(deliveryStore.findById(id).getStatus())
                .isEqualTo(WebhookDeliveryStatus.SUCCESS.name());
    }

    @Test
    void shouldRefuseToRetryASuccessfulDelivery() {
        long id = seedPendingDelivery(0, 0L);
        jdbcTemplate.update("UPDATE t_webhook_delivery SET status = 'SUCCESS' WHERE id = ?", id);

        assertThatThrownBy(() -> service.retry(id)).isInstanceOf(BizException.class)
                .hasMessageContaining("投递已成功");

        assertThatThrownBy(() -> service.retry(9L)).isInstanceOf(BizException.class)
                .hasMessageContaining("投递记录不存在");
    }

    @Test
    void shouldPageDeliveriesWithFilters() {
        long id = seedPendingDelivery(0, 0L);
        seedSubscription(3, 5000L);
        jdbcTemplate.update("INSERT INTO t_webhook_delivery (subscription_id, event_type, payload,"
                + " status, attempt, next_retry_at, created_at) VALUES (?, 'open.apikey.rotated', '{}',"
                + " 'FAILED', 1, ?, ?)", 6L, System.currentTimeMillis(), System.currentTimeMillis());

        assertThat(service.page(5L, null, null, 1, 10).total()).isEqualTo(1);
        assertThat(service.page(null, "open.apikey.rotated", null, 1, 10).total()).isEqualTo(1);
        assertThat(service.page(null, null, "FAILED", 1, 10).total()).isEqualTo(1);
        assertThat(service.page(null, null, "PENDING", 1, 10).total()).isEqualTo(1);
        assertThat(service.page(null, null, null, 1, 10).total()).isEqualTo(2);
    }

    private long seedSubscription(Integer maxRetry, long timeoutMillis) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setId(5L);
        subscription.setName("demo");
        subscription.setUrl("https://example.com/hook");
        subscription.setSecret("whsec");
        subscription.setEvents("*");
        subscription.setEnabled(Boolean.TRUE);
        subscription.setMaxRetry(maxRetry);
        subscription.setTimeoutMillis(timeoutMillis);
        jdbcTemplate.update("INSERT INTO t_webhook_subscription (id, name, url, secret, events,"
                + " enabled, max_retry, timeout_millis, version, created_at, updated_at) VALUES"
                + " (5, 'demo', 'https://example.com/hook', 'whsec', '*', 1, ?, ?, 0, ?, ?)",
                maxRetry == null ? 0 : maxRetry, timeoutMillis, System.currentTimeMillis(),
                System.currentTimeMillis());
        return 5L;
    }

    private long seedPendingDelivery(int attempt, long nextRetryAt) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setSubscriptionId(5L);
        delivery.setEventType("open.apikey.created");
        delivery.setPayload("{\"id\":1}");
        delivery.setStatus(WebhookDeliveryStatus.PENDING.name());
        delivery.setAttempt(attempt);
        delivery.setNextRetryAt(nextRetryAt);
        return deliveryStore.insertPending(delivery);
    }

    private static final class FakeWebhookSender implements WebhookSender {

        private final Deque<WebhookSendResult> results = new ArrayDeque<>();

        private int calls;

        private WebhookSendRequest lastRequest;

        void enqueue(WebhookSendResult... queued) {
            for (WebhookSendResult result : queued) {
                results.add(result);
            }
        }

        int calls() {
            return calls;
        }

        WebhookSendRequest lastRequest() {
            return lastRequest;
        }

        @Override
        public WebhookSendResult send(WebhookSendRequest request) {
            calls++;
            lastRequest = request;
            WebhookSendResult result = results.poll();
            return result == null ? WebhookSendResult.delivered(200, "ok") : result;
        }
    }
}
