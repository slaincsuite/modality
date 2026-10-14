package cn.slainc.modules.open.publisher;

import cn.slainc.modules.open.entity.WebhookSubscription;
import cn.slainc.modules.open.service.webhook.WebhookDeliveryAdminService;
import cn.slainc.modules.open.service.webhook.WebhookSubscriptionAdminService;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.spi.WebhookDeliveryCommand;
import cn.slainc.webhook.support.WebhookEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultWebhookPublisherTest {

    private final WebhookSubscriptionAdminService subscriptionService = mock(WebhookSubscriptionAdminService.class);

    private final WebhookDeliveryAdminService deliveryService = mock(WebhookDeliveryAdminService.class);

    private final WebhookProperties properties = new WebhookProperties();

    private final DefaultWebhookPublisher publisher = new DefaultWebhookPublisher(subscriptionService,
            deliveryService, properties, JsonMapper.builder().build());

    @Test
    void shouldQueueDeliveriesForTheMatchedSubscriptions() {
        when(subscriptionService.findEnabledByEvent(WebhookEventTypes.API_KEY_CREATED))
                .thenReturn(List.of(subscription(1L, "whsec-1"), subscription(3L, null)));
        AtomicLong ids = new AtomicLong(100L);
        when(deliveryService.createPending(any(WebhookDeliveryCommand.class)))
                .thenAnswer(invocation -> ids.incrementAndGet());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", WebhookEventTypes.API_KEY_CREATED);
        payload.put("id", 1);

        publisher.publish(WebhookEventTypes.API_KEY_CREATED, payload);

        ArgumentCaptor<WebhookDeliveryCommand> captor = ArgumentCaptor.forClass(WebhookDeliveryCommand.class);
        verify(deliveryService, times(2)).createPending(captor.capture());
        assertThat(captor.getAllValues()).extracting(WebhookDeliveryCommand::subscriptionId)
                .containsExactly(1L, 3L);
        assertThat(captor.getAllValues()).extracting(WebhookDeliveryCommand::eventType)
                .containsOnly(WebhookEventTypes.API_KEY_CREATED);
        assertThat(captor.getAllValues().get(0).payload()).contains(WebhookEventTypes.API_KEY_CREATED);
        assertThat(captor.getAllValues().get(0).url()).isEqualTo("https://example.com/hook/1");
        assertThat(captor.getAllValues().get(0).secret()).isEqualTo("whsec-1");
        verify(deliveryService, times(2)).deliverAsync(any());
    }

    @Test
    void shouldPassAStringPayloadThrough() {
        when(subscriptionService.findEnabledByEvent(WebhookEventTypes.API_KEY_ROTATED))
                .thenReturn(List.of(subscription(1L, "whsec-1")));
        when(deliveryService.createPending(any(WebhookDeliveryCommand.class))).thenReturn(7L);

        publisher.publish(WebhookEventTypes.API_KEY_ROTATED, "{\"raw\":true}");

        ArgumentCaptor<WebhookDeliveryCommand> captor = ArgumentCaptor.forClass(WebhookDeliveryCommand.class);
        verify(deliveryService).createPending(captor.capture());
        assertThat(captor.getValue().payload()).isEqualTo("{\"raw\":true}");
        verify(deliveryService).deliverAsync(7L);
    }

    @Test
    void shouldIgnoreBlankEventsAndTheDisabledSwitch() {
        properties.setEnabled(false);

        publisher.publish("  ", Map.of("id", 1));
        publisher.publish(WebhookEventTypes.API_KEY_CREATED, Map.of("id", 1));

        verify(deliveryService, never()).createPending(any(WebhookDeliveryCommand.class));
        verify(deliveryService, never()).deliverAsync(any());
    }

    @Test
    void shouldRunTheDispatchAfterCommitWhenATransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish(WebhookEventTypes.API_KEY_CREATED, Map.of("id", 1));

            verify(deliveryService, never()).createPending(any(WebhookDeliveryCommand.class));
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            when(subscriptionService.findEnabledByEvent(WebhookEventTypes.API_KEY_CREATED))
                    .thenReturn(List.of(subscription(1L, null)));
            synchronizations.get(0).afterCommit();

            verify(deliveryService).createPending(any(WebhookDeliveryCommand.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static WebhookSubscription subscription(Long id, String secret) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setId(id);
        subscription.setName("sub-" + id);
        subscription.setUrl("https://example.com/hook/" + id);
        subscription.setSecret(secret);
        subscription.setEvents("*");
        subscription.setEnabled(Boolean.TRUE);
        return subscription;
    }
}
