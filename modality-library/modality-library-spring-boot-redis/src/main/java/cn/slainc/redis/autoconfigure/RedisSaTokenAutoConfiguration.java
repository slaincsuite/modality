package cn.slainc.redis.autoconfigure;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.slainc.redis.dao.RedisSaTokenDao;
import cn.slainc.redis.dao.SaTokenRedisStore;
import cn.slainc.redis.serialize.RedisSerializers;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ConditionalOnClass(name = "cn.dev33.satoken.dao.SaTokenDao")
@ConditionalOnProperty(prefix = "modality.redis.satoken", name = "enabled", matchIfMissing = true)
public class RedisSaTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SaTokenRedisStore saTokenRedisStore(StringRedisTemplate stringRedisTemplate,
                                               RedisTemplate<String, Object> redisTemplate,
                                               RedisProperties properties) {
        return new SaTokenRedisStore(stringRedisTemplate, redisTemplate, RedisSerializers.plainMapper(),
                properties.getKey().getScanCount());
    }

    @Bean
    @ConditionalOnMissingBean(SaTokenDao.class)
    public RedisSaTokenDao redisSaTokenDao(SaTokenRedisStore saTokenRedisStore) {
        return new RedisSaTokenDao(saTokenRedisStore);
    }
}
