package cn.slainc.redis.autoconfigure;

import cn.slainc.redis.key.RedisKeyManager;
import cn.slainc.redis.queue.RedisDelayQueue;
import cn.slainc.redis.stream.RedisStreamTemplate;
import cn.slainc.redis.structure.RedisBitMap;
import cn.slainc.redis.structure.RedisGeo;
import cn.slainc.redis.structure.RedisHyperLogLog;
import cn.slainc.redis.structure.RedisPipeline;
import cn.slainc.redis.structure.RedisScriptExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class RedisStructureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.redis.key", name = "enabled", matchIfMissing = true)
    public RedisKeyManager redisKeyManager(StringRedisTemplate stringRedisTemplate,
                                           RedisTemplate<String, Object> redisTemplate,
                                           RedisProperties properties) {
        return new RedisKeyManager(stringRedisTemplate, redisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.redis.stream", name = "enabled", matchIfMissing = true)
    public RedisStreamTemplate redisStreamTemplate(StringRedisTemplate stringRedisTemplate,
                                                   RedisProperties properties) {
        return new RedisStreamTemplate(stringRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.redis.queue", name = "enabled", matchIfMissing = true)
    public RedisDelayQueue redisDelayQueue(StringRedisTemplate stringRedisTemplate,
                                           RedisProperties properties) {
        return new RedisDelayQueue(stringRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisBitMap redisBitMap(RedisTemplate<String, Object> redisTemplate) {
        return new RedisBitMap(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisHyperLogLog redisHyperLogLog(RedisTemplate<String, Object> redisTemplate) {
        return new RedisHyperLogLog(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisGeo redisGeo(RedisTemplate<String, Object> redisTemplate) {
        return new RedisGeo(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisPipeline redisPipeline(RedisTemplate<String, Object> redisTemplate) {
        return new RedisPipeline(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisScriptExecutor redisScriptExecutor(StringRedisTemplate stringRedisTemplate) {
        return new RedisScriptExecutor(stringRedisTemplate);
    }
}
