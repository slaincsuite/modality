package cn.slainc.modules.open.service.webhook;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.open.dto.WebhookSubscriptionRequest;
import cn.slainc.modules.open.entity.WebhookSubscription;
import cn.slainc.modules.open.store.JdbcWebhookDeliveryStore;
import cn.slainc.modules.open.store.JdbcWebhookSubscriptionStore;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.support.WebhookEventTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSubscriptionAdminServiceTest {

    private final WebhookProperties properties = new WebhookProperties();

    private JdbcTemplate jdbcTemplate;

    private JdbcWebhookSubscriptionStore subscriptionStore;

    private WebhookSubscriptionAdminService service;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:webhook-subscription-" + UUID.randomUUID().toString().replace("-", "")
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new cn.slainc.core.jdbc.ClasspathSchemaInitializer(dataSource, "open-schema.sql").init();
        jdbcTemplate = new JdbcTemplate(dataSource);
        subscriptionStore = new JdbcWebhookSubscriptionStore(jdbcTemplate);
        service = new WebhookSubscriptionAdminService(subscriptionStore,
                new JdbcWebhookDeliveryStore(jdbcTemplate), properties, null);
    }

    @Test
    void shouldApplyThePlatformDefaultsOnCreate() {
        Map<String, Object> created = service.create(new WebhookSubscriptionRequest(" order hook ",
                " https://example.com/hook ", null, null, null, null, null, null));

        assertThat(created.get("id")).isNotNull();
        assertThat(created.get("name")).isEqualTo("order hook");
        assertThat(created.get("url")).isEqualTo("https://example.com/hook");
        assertThat(created.get("events")).isEqualTo(WebhookEventTypes.WILDCARD);
        assertThat(created.get("enabled")).isEqualTo(Boolean.TRUE);
        assertThat(created.get("maxRetry")).isEqualTo(properties.getMaxRetry());
        assertThat(created.get("timeoutMillis")).isEqualTo(properties.getTimeoutMillis());
        assertThat(created.get("secretMasked")).isNull();
    }

    @Test
    void shouldRefuseToCreateAnIncompleteSubscription() {
        assertThatThrownBy(() -> service.create(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.create(new WebhookSubscriptionRequest(null, null, null, null, null,
                null, null, null))).isInstanceOf(BizException.class)
                .hasMessageContaining("订阅名称不能为空");
        assertThatThrownBy(() -> service.create(new WebhookSubscriptionRequest("demo", " ", null, null, null,
                null, null, null))).isInstanceOf(BizException.class)
                .hasMessageContaining("回调地址不能为空");
    }

    @Test
    void shouldKeepTheExistingSecretWhenTheUpdateOmitsIt() {
        long id = seedSubscription();

        service.update(id, new WebhookSubscriptionRequest("renamed", null, null, null, null, null, null, null));

        WebhookSubscription stored = subscriptionStore.findById(id);
        assertThat(stored.getSecret()).isEqualTo("whsec");
        assertThat(stored.getUrl()).isEqualTo("https://example.com/hook");
        assertThat(stored.getName()).isEqualTo("renamed");
        assertThat(stored.getVersion()).isEqualTo(1);
    }

    @Test
    void shouldClearTheSecretWhenAnEmptyStringIsSubmitted() {
        long id = seedSubscription();

        service.update(id, new WebhookSubscriptionRequest(null, null, " ", null, null, null, null, null));

        assertThat(subscriptionStore.findById(id).getSecret()).isNull();
    }

    @Test
    void shouldRejectAStaleUpdateWithAConflict() {
        long id = seedSubscription();
        service.update(id, new WebhookSubscriptionRequest("first edit", null, null, null, null, null, null,
                null));

        // 库里 version 已到 1，再拿旧 version=0 提交必须被拒绝
        assertThatThrownBy(() -> service.update(id, new WebhookSubscriptionRequest("stale", null, null, null,
                null, null, null, 0)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订阅已被他人修改");
    }

    @Test
    void shouldRemoveTheDeliveriesWithTheSubscription() {
        long id = seedSubscription();
        jdbcTemplate.update("INSERT INTO t_webhook_delivery (subscription_id, event_type, payload, status,"
                + " attempt, next_retry_at, created_at) VALUES (?, 'open.apikey.created', '{}', 'PENDING', 0,"
                + " ?, ?)", id, System.currentTimeMillis(), System.currentTimeMillis());

        assertThat(service.remove(List.of(id))).isEqualTo(1);

        assertThat(subscriptionStore.findById(id)).isNull();
        Integer deliveries = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_webhook_delivery",
                Integer.class);
        assertThat(deliveries).isZero();
        assertThat(service.remove(List.of())).isZero();
        assertThat(service.remove(null)).isZero();
    }

    @Test
    void shouldExposeTheBuiltInEvents() {
        assertThat(service.events()).extracting(WebhookEventTypes.EventType::name)
                .contains(WebhookEventTypes.API_KEY_CREATED, WebhookEventTypes.WEBHOOK_SUBSCRIPTION_REMOVED);
    }

    @Test
    void shouldPageAndFilterTheSubscriptions() {
        seedSubscription();
        service.create(new WebhookSubscriptionRequest("billing hook", "https://example.com/billing", null,
                "open.apikey.*", null, null, null, null));

        assertThat(service.page(null, null, 1, 10).total()).isEqualTo(2);
        assertThat(service.page("billing", null, 1, 10).total()).isEqualTo(1);
        assertThat(service.page(null, "billing", null, 1, 10).total()).isEqualTo(1);
        assertThat(service.page(null, "example.com/billing", null, 1, 10).total()).isEqualTo(1);
        assertThat(service.page(null, Boolean.FALSE, 1, 10).total()).isZero();
        assertThat(service.page(null, Boolean.TRUE, 1, 10).total()).isEqualTo(2);
    }

    @Test
    void shouldOnlyReturnEnabledSubscriptionsMatchingTheEvent() {
        service.create(new WebhookSubscriptionRequest("apikey hook", "https://example.com/a", null,
                "open.apikey.*", null, null, null, null));
        service.create(new WebhookSubscriptionRequest("upms hook", "https://example.com/u", null,
                "upms.*", null, null, null, null));
        service.create(new WebhookSubscriptionRequest("all hook", "https://example.com/all", null,
                "*", null, null, null, null));
        service.create(new WebhookSubscriptionRequest("off hook", "https://example.com/off", null,
                "open.apikey.*", Boolean.FALSE, null, null, null));

        assertThat(service.findEnabledByEvent(WebhookEventTypes.API_KEY_CREATED))
                .extracting(WebhookSubscription::getName)
                .containsExactly("apikey hook", "all hook");
        assertThat(service.findEnabledByEvent("   ")).isEmpty();
        assertThat(service.findEnabledByEvent(null)).isEmpty();
    }

    @Test
    void shouldTolerateAnEmptySubscriptionTable() {
        assertThat(service.findEnabledByEvent(WebhookEventTypes.API_KEY_CREATED)).isEmpty();
    }

    @Test
    void shouldDetailWithAMaskedSecret() {
        long id = seedSubscription();

        Map<String, Object> detail = service.detail(id);

        assertThat(detail.get("secretMasked")).isEqualTo("wh*ec");
        assertThatThrownBy(() -> service.detail(404L)).isInstanceOf(BizException.class)
                .hasMessageContaining("订阅不存在");
    }

    private long seedSubscription() {
        Map<String, Object> created = service.create(new WebhookSubscriptionRequest("demo",
                "https://example.com/hook", "whsec", "*", null, null, null, null));
        return ((Number) created.get("id")).longValue();
    }
}
