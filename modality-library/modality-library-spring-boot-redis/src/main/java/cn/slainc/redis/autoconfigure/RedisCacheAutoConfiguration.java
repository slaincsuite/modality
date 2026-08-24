package cn.slainc.redis.autoconfigure;

import cn.slainc.redis.cache.CacheInvalidationListener;
import cn.slainc.redis.cache.CacheTemplate;
import cn.slainc.redis.cache.RedisCacheFactory;
import cn.slainc.redis.cache.TwoLevelCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@AutoConfigureBefore(name = "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.redis.cache", name = "enabled", matchIfMissing = true)
public class RedisCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheTemplate cacheTemplate(RedisTemplate<String, Object> redisTemplate,
                                       StringRedisTemplate stringRedisTemplate,
                                       RedisProperties properties) {
        return new CacheTemplate(redisTemplate, stringRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheInvalidationListener cacheInvalidationListener(RedisMessageListenerContainer container) {
        CacheInvalidationListener listener = new CacheInvalidationListener();
        container.addMessageListener(listener, new ChannelTopic(CacheInvalidationListener.channel()));
        return listener;
    }

    @Bean
    @ConditionalOnMissingBean
    public TwoLevelCache twoLevelCache(RedisTemplate<String, Object> redisTemplate,
                                       StringRedisTemplate stringRedisTemplate,
                                       RedisProperties properties,
                                       CacheInvalidationListener cacheInvalidationListener) {
        return new TwoLevelCache(redisTemplate, stringRedisTemplate, properties, cacheInvalidationListener);
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnProperty(prefix = "modality.redis.cache", name = "spring-cache-enabled", matchIfMissing = true)
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                          RedisProperties properties,
                                          RedisSerializer<Object> redisValueSerializer) {
        return RedisCacheFactory.create(connectionFactory, properties, redisValueSerializer);
    }
}
