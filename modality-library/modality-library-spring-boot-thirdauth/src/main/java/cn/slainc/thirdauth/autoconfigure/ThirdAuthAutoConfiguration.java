package cn.slainc.thirdauth.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.thirdauth.ThirdAuthService;
import cn.slainc.thirdauth.binding.InMemoryThirdAccountBindingStore;
import cn.slainc.thirdauth.binding.ThirdAccountBindingStore;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import cn.slainc.thirdauth.provider.FeishuAuthProvider;
import cn.slainc.thirdauth.provider.ThirdPartyAuthProvider;
import cn.slainc.thirdauth.qr.MemoryQrTicketStore;
import cn.slainc.thirdauth.qr.QrTicketStore;
import cn.slainc.thirdauth.qr.RedisQrTicketStore;
import cn.slainc.thirdauth.state.MemoryStateStore;
import cn.slainc.thirdauth.state.RedisStateStore;
import cn.slainc.thirdauth.state.StateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@AutoConfiguration(after = ThirdAuthJdbcAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.thirdauth", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ThirdAuthProperties.class)
public class ThirdAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ThirdAuthConfigRegistry thirdAuthConfigRegistry(ThirdAuthProperties properties,
                                                           ObjectProvider<DynamicConfigService> configService) {
        return new ThirdAuthConfigRegistry(properties, configService);
    }

    @Bean
    @ConditionalOnMissingBean(StateStore.class)
    public StateStore thirdAuthStateStore(ObjectProvider<StringRedisTemplate> redisTemplate) {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        return template == null ? new MemoryStateStore() : new RedisStateStore(template);
    }

    @Bean
    @ConditionalOnMissingBean(QrTicketStore.class)
    public QrTicketStore thirdAuthQrTicketStore(ObjectProvider<StringRedisTemplate> redisTemplate) {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        return template == null ? new MemoryQrTicketStore() : new RedisQrTicketStore(template);
    }

    @Bean
    @ConditionalOnMissingBean(ThirdAccountBindingStore.class)
    public InMemoryThirdAccountBindingStore thirdAccountBindingStore() {
        return new InMemoryThirdAccountBindingStore();
    }

    @Bean
    @ConditionalOnMissingBean(name = "feishuAuthProvider")
    public FeishuAuthProvider feishuAuthProvider(ThirdAuthConfigRegistry configRegistry) {
        return new FeishuAuthProvider(configRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThirdAuthService thirdAuthService(ThirdAuthConfigRegistry configRegistry,
                                             List<ThirdPartyAuthProvider> providers,
                                             StateStore stateStore,
                                             QrTicketStore qrTicketStore,
                                             ThirdAccountBindingStore bindingStore,
                                             ThirdAuthProperties properties) {
        return new ThirdAuthService(configRegistry, providers, stateStore, qrTicketStore, bindingStore, properties);
    }
}
