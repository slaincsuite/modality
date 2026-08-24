package cn.slainc.redis.cache;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.serialize.RedisSerializers;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

public final class RedisCacheFactory {

    private RedisCacheFactory() {
    }

    public static CacheManager create(RedisConnectionFactory connectionFactory,
                                      RedisProperties properties,
                                      RedisSerializer<Object> valueSerializer) {
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager
                .builder(connectionFactory)
                .cacheDefaults(defaultConfiguration(properties, valueSerializer));
        return builder.build();
    }

    public static RedisCacheConfiguration defaultConfiguration(RedisProperties properties,
                                                               RedisSerializer<Object> valueSerializer) {
        RedisSerializer<Object> serializer = valueSerializer == null ? RedisSerializers.json() : valueSerializer;
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.getCache().getDefaultTtl())
                .computePrefixWith(name -> CacheKeys.PREFIX + "cache:" + name + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializers.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
    }
}
