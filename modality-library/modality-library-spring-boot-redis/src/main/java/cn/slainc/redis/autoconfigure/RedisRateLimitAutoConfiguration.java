package cn.slainc.redis.autoconfigure;

import cn.slainc.redis.ratelimit.RateLimitAspect;
import cn.slainc.redis.ratelimit.RateLimiter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.redis.rate-limit", name = "enabled", matchIfMissing = true)
public class RedisRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter(StringRedisTemplate stringRedisTemplate, RedisProperties properties) {
        return new RateLimiter(stringRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimiter rateLimiter, RedisProperties properties) {
        return new RateLimitAspect(rateLimiter, properties);
    }
}
