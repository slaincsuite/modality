package cn.slainc.search.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.search.config.DynamicSearchProperties;
import cn.slainc.search.config.SearchProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@AutoConfiguration(before = SearchAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SearchDynamicConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(DynamicSearchProperties.class)
    public SearchProperties dynamicSearchProperties(Environment environment,
                                                    ObjectProvider<DynamicConfigService> configService) {
        SearchProperties bound = Binder.get(environment).bind("modality.search", Bindable.of(SearchProperties.class))
                .orElseGet(SearchProperties::new);
        return configService.getIfAvailable() == null ? bound
                : new DynamicSearchProperties(bound, configService);
    }
}
