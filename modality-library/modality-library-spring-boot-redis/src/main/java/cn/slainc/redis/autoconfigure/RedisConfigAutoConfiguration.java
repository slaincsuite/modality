package cn.slainc.redis.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.redis.config.ConfigBroadcaster;
import cn.slainc.redis.config.ConfigValueCipher;
import cn.slainc.redis.config.DynamicConfigRepository;
import cn.slainc.redis.config.GcmConfigValueCipher;
import cn.slainc.redis.config.InMemoryDynamicConfigRepository;
import cn.slainc.redis.config.RedisConfigBroadcaster;
import cn.slainc.redis.config.RedisConfigChangeSubscriber;
import cn.slainc.redis.config.RedisDynamicConfigService;
import cn.slainc.redis.config.UnavailableConfigValueCipher;
import cn.slainc.redis.serialize.RedisSerializers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.redis.config", name = "enabled", matchIfMissing = true)
public class RedisConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConfigValueCipher configValueCipher(ObjectProvider<CryptoKeyProvider> keyProvider) {
        CryptoKeyProvider provider = keyProvider.getIfAvailable();
        return provider == null ? new UnavailableConfigValueCipher() : new GcmConfigValueCipher(provider);
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicConfigRepository dynamicConfigRepository() {
        return new InMemoryDynamicConfigRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.redis.config", name = "publish-enabled", matchIfMissing = true)
    public ConfigBroadcaster configBroadcaster(StringRedisTemplate stringRedisTemplate,
                                               RedisProperties properties) {
        return new RedisConfigBroadcaster(stringRedisTemplate, RedisSerializers.plainMapper(),
                RedisDynamicConfigService.channelOf(properties));
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisDynamicConfigService redisDynamicConfigService(StringRedisTemplate stringRedisTemplate,
                                                              ObjectProvider<DynamicConfigRepository> repository,
                                                              ObjectProvider<ConfigValueCipher> cipher,
                                                              ObjectProvider<ConfigBroadcaster> broadcaster,
                                                              ObjectProvider<ApplicationEventPublisher> eventPublisher,
                                                              RedisProperties properties) {
        return new RedisDynamicConfigService(stringRedisTemplate,
                repository.getIfAvailable(),
                RedisSerializers.plainMapper(),
                cipher.getIfAvailable(),
                broadcaster.getIfAvailable(),
                eventPublisher.getIfAvailable(),
                properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicConfigService dynamicConfigService(RedisDynamicConfigService redisDynamicConfigService) {
        return redisDynamicConfigService;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.redis.config", name = "subscribe-enabled", matchIfMissing = true)
    public RedisConfigChangeSubscriber redisConfigChangeSubscriber(RedisDynamicConfigService configService,
                                                                  RedisMessageListenerContainer container) {
        RedisConfigChangeSubscriber subscriber = new RedisConfigChangeSubscriber(configService,
                RedisSerializers.plainMapper());
        container.addMessageListener(subscriber, new ChannelTopic(configService.channel()));
        return subscriber;
    }
}
