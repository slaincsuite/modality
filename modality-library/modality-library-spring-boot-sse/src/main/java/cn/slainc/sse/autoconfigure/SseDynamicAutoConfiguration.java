package cn.slainc.sse.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@AutoConfiguration(before = SseAutoConfiguration.class)
@ConditionalOnProperty(prefix = SseProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class SseDynamicAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(DynamicSseProperties.class)
    public SseProperties dynamicSseProperties(Environment environment,
                                              ObjectProvider<DynamicConfigService> serviceProvider) {
        SseProperties bound = Binder.get(environment).bind(SseProperties.PREFIX, Bindable.of(SseProperties.class))
                .orElseGet(SseProperties::new);
        DynamicConfigService service = serviceProvider.getIfAvailable();
        return service == null ? bound : new DynamicSseProperties(bound, service);
    }
}
