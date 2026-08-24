package cn.slainc.redis.autoconfigure;

import cn.slainc.redis.pubsub.RedisListenerRegistrar;
import cn.slainc.redis.pubsub.RedisPubSubService;
import cn.slainc.redis.serialize.RedisSerializers;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.redis.pubsub", name = "enabled", matchIfMissing = true)
public class RedisPubSubAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisPubSubService redisPubSubService(StringRedisTemplate stringRedisTemplate,
                                                 RedisMessageListenerContainer container) {
        return new RedisPubSubService(stringRedisTemplate, container, RedisSerializers.plainMapper());
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisListenerRegistrar redisListenerRegistrar(RedisPubSubService redisPubSubService,
                                                         ListableBeanFactory beanFactory,
                                                         RedisProperties properties) {
        return new RedisListenerRegistrar(redisPubSubService, beanFactory, properties);
    }
}
