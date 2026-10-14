package cn.slainc.webhook.support;

import cn.slainc.webhook.spi.WebhookSender;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.sender.WebhookSendRequest;
import cn.slainc.webhook.sender.WebhookSendResult;
import cn.slainc.webhook.spi.WebhookDeliveryCommand;
import cn.slainc.webhook.spi.WebhookDeliveryPort;
import cn.slainc.webhook.spi.WebhookDeliveryTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookDeliveryDispatcherTest {

    private final FakeWebhookSender sender = new FakeWebhookSender();

    private final WebhookProperties properties = new WebhookProperties();

    private final FakeWebhookDeliveryPort port = new FakeWebhookDeliveryPort();

    private final WebhookDeliveryDispatcher dispatcher = new WebhookDeliveryDispatcher(sender, properties, port);

    @Test
    void shouldMarkSuccessOnTheFirstAttempt() {
        sender.enqueue(WebhookSendResult.delivered(200, "ok"));

        dispatcher.deliver(task(9L, 0, 3));

        assertThat(sender.calls()).isEqualTo(1);
        assertThat(sender.lastRequest().deliveryId()).isEqualTo(9L);
        assertThat(sender.lastRequest().secret()).isEqualTo("whsec");
        assertThat(sender.lastRequest().timeoutMillis()).isEqualTo(5000L);
        assertThat(port.failures).isEmpty();
        assertThat(port.successes).hasSize(1);
        Success success = port.successes.get(0);
        assertThat(success.id()).isEqualTo(9L);
        assertThat(success.httpStatus()).isEqualTo(200);
        assertThat(success.responseBody()).isEqualTo("ok");
        assertThat(success.costMillis()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void shouldBackOffExponentiallyWhileRetriesRemain() {
        LocalDateTime before = LocalDateTime.now();
        sender.enqueue(WebhookSendResult.failed("connection refused"));

        dispatcher.deliver(task(9L, 0, 3));

        assertThat(port.successes).isEmpty();
        assertThat(port.failures).hasSize(1);
        Failure first = port.failures.get(0);
        assertThat(first.attempt()).isEqualTo(1);
        assertThat(first.httpStatus()).isZero();
        assertThat(first.errorMessage()).isEqualTo("connection refused");
        assertThat(first.exhausted()).isFalse();
        assertThat(Duration.between(before, first.nextRetryAt()))
                .isBetween(Duration.ofSeconds(30), Duration.ofSeconds(35));

        sender.enqueue(WebhookSendResult.rejected(500, "boom", "HTTP 500"));
        dispatcher.deliver(task(9L, 1, 3));

        Failure second = port.failures.get(1);
        assertThat(second.attempt()).isEqualTo(2);
        assertThat(second.httpStatus()).isEqualTo(500);
        assertThat(second.errorMessage()).isEqualTo("HTTP 500");
        assertThat(second.exhausted()).isFalse();
        assertThat(second.nextRetryAt()).isAfter(first.nextRetryAt());
        assertThat(Duration.between(before, second.nextRetryAt()))
                .isBetween(Duration.ofSeconds(60), Duration.ofSeconds(65));
    }

    @Test
    void shouldExhaustTheDeliveryWhenTheRetriesRunOut() {
        sender.enqueue(WebhookSendResult.failed("first"), WebhookSendResult.failed("second"));

        dispatcher.deliver(task(9L, 0, 2));
        dispatcher.deliver(task(9L, 1, 2));

        assertThat(port.failures).hasSize(2);
        assertThat(port.failures.get(0).exhausted()).isFalse();
        Failure exhausted = port.failures.get(1);
        assertThat(exhausted.attempt()).isEqualTo(2);
        assertThat(exhausted.exhausted()).isTrue();
        assertThat(exhausted.nextRetryAt()).isNull();
        assertThat(exhausted.errorMessage()).isEqualTo("second");
    }

    @Test
    void shouldFallBackToOneAttemptWhenTheSubscriptionHasNoRetryLimit() {
        sender.enqueue(WebhookSendResult.failed("boom"));

        dispatcher.deliver(task(9L, 0, 0));

        assertThat(port.failures).hasSize(1);
        assertThat(port.failures.get(0).exhausted()).isTrue();
    }

    @Test
    void shouldTreatASenderFailureAsAFailedAttempt() {
        sender.failWith(new IllegalStateException("tls handshake failed"));

        dispatcher.deliver(task(9L, 0, 3));

        assertThat(port.successes).isEmpty();
        assertThat(port.failures).hasSize(1);
        assertThat(port.failures.get(0).errorMessage()).isEqualTo("tls handshake failed");
    }

    @Test
    void shouldTruncateTheErrorMessage() {
        String message = "x".repeat(2000);
        sender.enqueue(WebhookSendResult.failed(message));

        dispatcher.deliver(task(9L, 0, 3));

        assertThat(port.failures.get(0).errorMessage()).hasSize(1024);
    }

    @Test
    void shouldScanAndDeliverDueTasksInBatch() {
        port.due.add(task(1L, 0, 3));
        port.due.add(task(2L, 1, 3));
        sender.enqueue(WebhookSendResult.delivered(204, null), WebhookSendResult.failed("boom"));

        assertThat(dispatcher.retryDue(50)).isEqualTo(2);

        assertThat(port.lastLimit).isEqualTo(50);
        assertThat(sender.calls()).isEqualTo(2);
        assertThat(port.successes).hasSize(1);
        assertThat(port.failures).hasSize(1);
    }

    @Test
    void shouldNormalizeTheBatchSizeAndTolerateAnEmptyScan() {
        assertThat(dispatcher.retryDue(0)).isZero();
        assertThat(port.lastLimit).isEqualTo(1);
    }

    @Test
    void shouldIgnoreAnEmptyTask() {
        dispatcher.deliver(null);
        dispatcher.deliver(new WebhookDeliveryTask(null, null, null, null, null, null, 0, 3, 0L));

        assertThat(sender.calls()).isZero();
        assertThat(port.successes).isEmpty();
        assertThat(port.failures).isEmpty();
    }

    @Test
    void shouldResolveThePortLazilyAndSkipWhenItIsAbsent() {
        WebhookDeliveryDispatcher withoutPort = new WebhookDeliveryDispatcher(sender, properties,
                emptyProvider());

        withoutPort.deliver(task(9L, 0, 3));

        assertThat(withoutPort.retryDue(10)).isZero();
        assertThat(sender.calls()).isZero();
    }

    @Test
    void shouldSwallowPortResolutionFailures() {
        ObjectProvider<WebhookDeliveryPort> provider = failingProvider();

        WebhookDeliveryDispatcher failingPort = new WebhookDeliveryDispatcher(sender, properties, provider);

        assertThat(failingPort.retryDue(10)).isZero();
        failingPort.deliver(task(9L, 0, 3));
        assertThat(sender.calls()).isZero();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<WebhookDeliveryPort> emptyProvider() {
        ObjectProvider<WebhookDeliveryPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<WebhookDeliveryPort> failingProvider() {
        ObjectProvider<WebhookDeliveryPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenThrow(new IllegalStateException("port bean failed to start"));
        return provider;
    }

    private static WebhookDeliveryTask task(Long deliveryId, int attempt, int maxRetry) {
        return new WebhookDeliveryTask(deliveryId, 5L, "open.apikey.created", "{\"id\":1}",
                "https://example.com/hook", "whsec", attempt, maxRetry, 5000L);
    }

    private record Success(Long id, int httpStatus, String responseBody, long costMillis) {
    }

    private record Failure(Long id, int attempt, int httpStatus, String responseBody, String errorMessage,
                           LocalDateTime nextRetryAt, boolean exhausted, long costMillis) {
    }

    private static final class FakeWebhookDeliveryPort implements WebhookDeliveryPort {

        private final List<Success> successes = new ArrayList<>();

        private final List<Failure> failures = new ArrayList<>();

        private final List<WebhookDeliveryTask> due = new ArrayList<>();

        private int lastLimit;

        @Override
        public Long createPending(WebhookDeliveryCommand command) {
            return null;
        }

        @Override
        public List<WebhookDeliveryTask> findDue(int limit) {
            lastLimit = limit;
            return List.copyOf(due);
        }

        @Override
        public void markSuccess(Long id, int httpStatus, String responseBody, long costMillis) {
            successes.add(new Success(id, httpStatus, responseBody, costMillis));
        }

        @Override
        public void markFailure(Long id, int attempt, int httpStatus, String responseBody, String errorMessage,
                                LocalDateTime nextRetryAt, boolean exhausted, long costMillis) {
            failures.add(new Failure(id, attempt, httpStatus, responseBody, errorMessage, nextRetryAt, exhausted,
                    costMillis));
        }
    }

    private static final class FakeWebhookSender implements WebhookSender {

        private final Deque<WebhookSendResult> results = new ArrayDeque<>();

        private RuntimeException failure;

        private int calls;

        private WebhookSendRequest lastRequest;

        void enqueue(WebhookSendResult... queued) {
            for (WebhookSendResult result : queued) {
                results.add(result);
            }
        }

        void failWith(RuntimeException error) {
            this.failure = error;
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
            if (failure != null) {
                throw failure;
            }
            WebhookSendResult result = results.poll();
            return result == null ? WebhookSendResult.delivered(200, "ok") : result;
        }
    }
}
