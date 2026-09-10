package cn.slainc.jwt.autoconfigure;

import cn.slainc.jwt.revoke.RedisRevocationStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(afterName = {"org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "cn.slainc.redis.autoconfigure.RedisAutoConfiguration"})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "modality.jwt", name = "revocation-store", havingValue = "redis", matchIfMissing = true)
public class JwtRedisStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisRevocationStore.class)
    public RedisRevocationStore redisRevocationStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisRevocationStore(stringRedisTemplate);
    }
}
