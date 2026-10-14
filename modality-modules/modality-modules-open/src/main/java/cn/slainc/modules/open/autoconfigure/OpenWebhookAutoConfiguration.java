package cn.slainc.modules.open.autoconfigure;

import cn.slainc.modules.open.publisher.DefaultWebhookPublisher;
import cn.slainc.modules.open.service.webhook.WebhookDeliveryAdminService;
import cn.slainc.modules.open.service.webhook.WebhookSubscriptionAdminService;
import cn.slainc.modules.open.store.JdbcWebhookDeliveryStore;
import cn.slainc.modules.open.store.JdbcWebhookSubscriptionStore;
import cn.slainc.modules.open.web.webhook.WebhookController;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.spi.WebhookPublisher;
import cn.slainc.webhook.support.WebhookDeliveryDispatcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

// Webhook 管理端点：JDBC 存储 + webhook 库派发器组合装配，宿主缺任一能力时整体不装配
@AutoConfiguration(afterName = {"cn.slainc.webhook.autoconfigure.WebhookAutoConfiguration",
        "cn.slainc.modules.open.autoconfigure.OpenStoreAutoConfiguration"})
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "modality.open", name = "enabled", matchIfMissing = true)
public class OpenWebhookAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JdbcWebhookSubscriptionStore openWebhookSubscriptionStore(JdbcTemplate jdbcTemplate) {
        return new JdbcWebhookSubscriptionStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcWebhookDeliveryStore openWebhookDeliveryStore(JdbcTemplate jdbcTemplate) {
        return new JdbcWebhookDeliveryStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookSubscriptionAdminService openWebhookSubscriptionAdminService(
            JdbcWebhookSubscriptionStore subscriptionStore, JdbcWebhookDeliveryStore deliveryStore,
            ObjectProvider<WebhookProperties> propertiesProvider,
            ObjectProvider<WebhookPublisher> publisherProvider) {
        return new WebhookSubscriptionAdminService(subscriptionStore, deliveryStore,
                propertiesProvider.getIfAvailable(WebhookProperties::new), publisherProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(WebhookDeliveryDispatcher.class)
    @ConditionalOnProperty(prefix = "modality.webhook", name = "enabled", matchIfMissing = true)
    public WebhookDeliveryAdminService openWebhookDeliveryAdminService(JdbcWebhookDeliveryStore deliveryStore,
                                                                       JdbcWebhookSubscriptionStore subscriptionStore,
                                                                       WebhookDeliveryDispatcher dispatcher,
                                                                       ObjectProvider<WebhookProperties> propertiesProvider,
                                                                       @Qualifier("webhookDeliveryExecutor")
                                                                       ObjectProvider<ThreadPoolTaskExecutor> executorProvider) {
        return new WebhookDeliveryAdminService(deliveryStore, subscriptionStore, dispatcher,
                propertiesProvider.getIfAvailable(WebhookProperties::new), executorProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(WebhookPublisher.class)
    @ConditionalOnBean(WebhookDeliveryDispatcher.class)
    @ConditionalOnProperty(prefix = "modality.webhook", name = "enabled", matchIfMissing = true)
    public WebhookPublisher openWebhookPublisher(WebhookSubscriptionAdminService subscriptionService,
                                                 WebhookDeliveryAdminService deliveryService,
                                                 ObjectProvider<WebhookProperties> propertiesProvider,
                                                 ObjectProvider<JsonMapper> jsonMapperProvider) {
        JsonMapper jsonMapper = jsonMapperProvider.getIfAvailable();
        return new DefaultWebhookPublisher(subscriptionService, deliveryService,
                propertiesProvider.getIfAvailable(WebhookProperties::new),
                jsonMapper == null ? JsonMapper.builder().build() : jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(WebhookDeliveryDispatcher.class)
    @ConditionalOnProperty(prefix = "modality.webhook", name = "enabled", matchIfMissing = true)
    public WebhookController openWebhookController(WebhookSubscriptionAdminService subscriptionService,
                                                   WebhookDeliveryAdminService deliveryService) {
        return new WebhookController(subscriptionService, deliveryService);
    }
}
