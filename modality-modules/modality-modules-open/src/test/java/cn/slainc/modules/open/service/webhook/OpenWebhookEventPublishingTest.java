package cn.slainc.modules.open.service.webhook;

import cn.slainc.modules.open.dto.WebhookSubscriptionRequest;
import cn.slainc.modules.open.store.JdbcWebhookDeliveryStore;
import cn.slainc.modules.open.store.JdbcWebhookSubscriptionStore;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.spi.WebhookPublisher;
import cn.slainc.webhook.support.WebhookEventTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 订阅的写操作必须真的把事件交给 WebhookPublisher，且载荷里绝不能出现签名密钥明文
class OpenWebhookEventPublishingTest {

    private final List<String> publishedEvents = new ArrayList<>();

    private final List<Map<String, Object>> publishedPayloads = new ArrayList<>();

    private final WebhookPublisher publisher = (eventType, payload) -> {
        publishedEvents.add(eventType);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) payload;
        publishedPayloads.add(map);
    };

    @SuppressWarnings("unchecked")
    private final ObjectProvider<WebhookPublisher> publisherProvider = mock(ObjectProvider.class);

    private JdbcTemplate jdbcTemplate;

    private WebhookSubscriptionAdminService service;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:webhook-events-" + UUID.randomUUID().toString().replace("-", "")
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new cn.slainc.core.jdbc.ClasspathSchemaInitializer(dataSource, "open-schema.sql").init();
        jdbcTemplate = new JdbcTemplate(dataSource);
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        service = new WebhookSubscriptionAdminService(new JdbcWebhookSubscriptionStore(jdbcTemplate),
                new JdbcWebhookDeliveryStore(jdbcTemplate), new WebhookProperties(), publisherProvider);
    }

    @Test
    void shouldPublishSubscriptionLifecycleEventsWithoutSigningSecrets() {
        Map<String, Object> created = service.create(new WebhookSubscriptionRequest("hook",
                "https://example.com/hook", "plain-secret", null, null, null, null, null));

        assertThat(publishedEvents).containsExactly(WebhookEventTypes.WEBHOOK_SUBSCRIPTION_CREATED);
        Map<String, Object> payload = publishedPayloads.get(0);
        assertThat(payload).containsEntry("name", "hook")
                .containsEntry("id", String.valueOf(created.get("id")))
                .containsEntry("url", "https://example.com/hook");
        assertThat(payload).doesNotContainKeys("secret", "secretMasked");

        service.update(((Number) created.get("id")).longValue(), new WebhookSubscriptionRequest("hook-2",
                null, null, null, null, null, null, null));
        service.remove(List.of(((Number) created.get("id")).longValue()));

        assertThat(publishedEvents).containsExactly(WebhookEventTypes.WEBHOOK_SUBSCRIPTION_CREATED,
                WebhookEventTypes.WEBHOOK_SUBSCRIPTION_UPDATED, WebhookEventTypes.WEBHOOK_SUBSCRIPTION_REMOVED);
        for (Map<String, Object> published : publishedPayloads) {
            assertThat(published).doesNotContainKeys("secret", "secretMasked");
        }
    }

    @Test
    void shouldStaySilentWithoutAPublisher() {
        when(publisherProvider.getIfAvailable()).thenReturn(null);
        service.create(new WebhookSubscriptionRequest("no-publisher", "https://example.com/hook",
                null, null, null, null, null, null));

        assertThat(publishedEvents).isEmpty();
    }
}
