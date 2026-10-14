package cn.slainc.webhook.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.webhook.config.DynamicWebhookProperties;
import cn.slainc.webhook.config.WebhookProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.webhook", name = "enabled", matchIfMissing = true)
public class WebhookDynamicConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(DynamicWebhookProperties.class)
    public WebhookProperties dynamicWebhookProperties(Environment environment,
                                                      ObjectProvider<DynamicConfigService> configService) {
        WebhookProperties bound = Binder.get(environment)
                .bind("modality.webhook", Bindable.of(WebhookProperties.class))
                .orElseGet(WebhookProperties::new);
        return configService.getIfAvailable() == null ? bound
                : new DynamicWebhookProperties(bound, configService);
    }
}
