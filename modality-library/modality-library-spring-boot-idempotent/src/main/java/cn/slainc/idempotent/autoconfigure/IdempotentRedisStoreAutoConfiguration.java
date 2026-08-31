package cn.slainc.idempotent.autoconfigure;

import cn.slainc.idempotent.store.IdempotentStore;
import cn.slainc.idempotent.store.RedisIdempotentStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = cn.slainc.redis.autoconfigure.RedisAutoConfiguration.class,
        afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass({StringRedisTemplate.class, RedisConnectionFactory.class})
@ConditionalOnProperty(prefix = IdempotentProperties.PREFIX + ".store", name = "type", havingValue = "redis",
        matchIfMissing = true)
public class IdempotentRedisStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisIdempotentStore redisIdempotentStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisIdempotentStore(stringRedisTemplate);
    }
}
