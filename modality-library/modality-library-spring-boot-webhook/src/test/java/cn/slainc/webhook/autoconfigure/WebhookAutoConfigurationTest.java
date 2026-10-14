package cn.slainc.webhook.autoconfigure;

import cn.slainc.core.config.DynamicConfig;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.webhook.spi.WebhookSender;
import cn.slainc.webhook.config.DynamicWebhookProperties;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.spi.WebhookDeliveryCommand;
import cn.slainc.webhook.spi.WebhookDeliveryPort;
import cn.slainc.webhook.spi.WebhookDeliveryTask;
import cn.slainc.webhook.support.WebhookDeliveryDispatcher;
import cn.slainc.webhook.support.WebhookRetryScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebhookDynamicConfiguration.class, WebhookAutoConfiguration.class));

    @Test
    void shouldStartWithoutADeliveryPort() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(WebhookSender.class);
            assertThat(context).hasSingleBean(WebhookDeliveryDispatcher.class);
            assertThat(context).hasSingleBean(WebhookRetryScheduler.class);
            assertThat(context).doesNotHaveBean(WebhookDeliveryPort.class);
            assertThat(context.getBean(WebhookDeliveryDispatcher.class).retryDue(10)).isZero();
        });
    }

    @Test
    void shouldResolveTheDeliveryPortFromTheContext() {
        FakeWebhookDeliveryPort port = new FakeWebhookDeliveryPort();

        runner.withPropertyValues("modality.webhook.retry-interval=0s")
                .withBean(WebhookDeliveryPort.class, () -> port).run(context -> {
            assertThat(context).hasSingleBean(WebhookDeliveryDispatcher.class);
            assertThat(context.getBean(WebhookDeliveryDispatcher.class).retryDue(5)).isZero();
            assertThat(port.findDueCalls).isEqualTo(1);
            assertThat(port.lastLimit).isEqualTo(5);
        });
    }

    @Test
    void shouldStayOffWhenTheLibraryIsDisabled() {
        runner.withPropertyValues("modality.webhook.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(WebhookDeliveryDispatcher.class);
            assertThat(context).doesNotHaveBean(WebhookRetryScheduler.class);
            assertThat(context).doesNotHaveBean(WebhookSender.class);
        });
    }

    @Test
    void shouldBindTheStaticProperties() {
        runner.withPropertyValues("modality.webhook.max-retry=9", "modality.webhook.timeout-millis=2500",
                "modality.webhook.initial-backoff=PT45S").run(context -> {
            assertThat(context).hasNotFailed();
            WebhookProperties properties = context.getBean(WebhookProperties.class);
            assertThat(properties.getMaxRetry()).isEqualTo(9);
            assertThat(properties.getTimeoutMillis()).isEqualTo(2500L);
            assertThat(properties.getInitialBackoff()).isEqualTo(Duration.ofSeconds(45));
            assertThat(properties.getSignatureHeader()).isEqualTo("X-Modality-Signature");
        });
    }

    @Test
    void shouldPreferTheDynamicPropertiesWhenAConfigServiceIsPresent() {
        FakeConfigService service = new FakeConfigService();
        service.put(DynamicWebhookProperties.KEY_MAX_RETRY, "9");
        service.put(DynamicWebhookProperties.KEY_INITIAL_BACKOFF, "PT45S");
        service.put(DynamicWebhookProperties.KEY_TIMEOUT_MILLIS, "2500");

        runner.withBean(DynamicConfigService.class, () -> service).run(context -> {
            assertThat(context).hasNotFailed();
            WebhookProperties properties = context.getBean(WebhookProperties.class);
            assertThat(properties).isInstanceOf(DynamicWebhookProperties.class);
            assertThat(properties.getMaxRetry()).isEqualTo(9);
            assertThat(properties.getInitialBackoff()).isEqualTo(Duration.ofSeconds(45));
            assertThat(properties.getTimeoutMillis()).isEqualTo(2500L);
        });
    }

    @Test
    void shouldParsePlainSecondsAndFallBackOnBadDurations() {
        FakeConfigService service = new FakeConfigService();
        service.put(DynamicWebhookProperties.KEY_MAX_BACKOFF, "120");
        service.put(DynamicWebhookProperties.KEY_INITIAL_BACKOFF, "not-a-duration");

        runner.withBean(DynamicConfigService.class, () -> service).run(context -> {
            WebhookProperties properties = context.getBean(WebhookProperties.class);
            assertThat(properties.getMaxBackoff()).isEqualTo(Duration.ofSeconds(120));
            assertThat(properties.getInitialBackoff()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void shouldFallBackToTheBoundPropertiesWithoutAConfigService() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WebhookProperties.class)).isNotInstanceOf(DynamicWebhookProperties.class);
        });
    }

    private static final class FakeWebhookDeliveryPort implements WebhookDeliveryPort {

        private int findDueCalls;

        private int lastLimit;

        @Override
        public Long createPending(WebhookDeliveryCommand command) {
            return null;
        }

        @Override
        public List<WebhookDeliveryTask> findDue(int limit) {
            findDueCalls++;
            lastLimit = limit;
            return List.of();
        }

        @Override
        public void markSuccess(Long id, int httpStatus, String responseBody, long costMillis) {
        }

        @Override
        public void markFailure(Long id, int attempt, int httpStatus, String responseBody, String errorMessage,
                                LocalDateTime nextRetryAt, boolean exhausted, long costMillis) {
        }
    }

    private static class FakeConfigService implements DynamicConfigService {

        private final Map<String, String> values = new ConcurrentHashMap<>();

        void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public Optional<String> findString(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public String getString(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            String value = values.get(key);
            return value == null ? defaultValue : Integer.parseInt(value);
        }

        @Override
        public long getLong(String key, long defaultValue) {
            String value = values.get(key);
            return value == null ? defaultValue : Long.parseLong(value);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            String value = values.get(key);
            return value == null ? defaultValue : Boolean.parseBoolean(value);
        }

        @Override
        public <T> T getObject(String key, Class<T> type) {
            return null;
        }

        @Override
        public <T> T getObject(String key, Class<T> type, T defaultValue) {
            return defaultValue;
        }

        @Override
        public <T> List<T> getList(String key, Class<T> elementType) {
            return List.of();
        }

        @Override
        public String getEncrypted(String key) {
            return null;
        }

        @Override
        public Map<String, String> getGroup(String group) {
            return Map.of();
        }

        @Override
        public List<DynamicConfig> listByGroup(String group) {
            return List.of();
        }

        @Override
        public void set(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void set(String key, String value, String group) {
            values.put(key, value);
        }

        @Override
        public void setEncrypted(String key, String value, String group) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }

        @Override
        public void refresh(String key) {
        }

        @Override
        public void refreshAll() {
        }
    }
}
