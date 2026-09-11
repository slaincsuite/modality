package cn.slainc.captcha.autoconfigure;

import cn.slainc.captcha.CaptchaMetrics;
import cn.slainc.captcha.CaptchaProperties;
import cn.slainc.captcha.CaptchaService;
import cn.slainc.captcha.CaptchaTypeResolver;
import cn.slainc.captcha.store.CaptchaStore;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.redis.autoconfigure.RedisConfigAutoConfiguration;
import cn.slainc.redis.ratelimit.RateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(value = RedisConfigAutoConfiguration.class,
        name = {"cn.slainc.redis.autoconfigure.RedisRateLimitAutoConfiguration",
                "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"})
@EnableConfigurationProperties(CaptchaProperties.class)
@ConditionalOnProperty(prefix = "modality.captcha", name = "enabled", matchIfMissing = true)
public class CaptchaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CaptchaMetrics.class)
    public CaptchaMetrics captchaMetrics() {
        return new CaptchaMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(CaptchaTypeResolver.class)
    public CaptchaTypeResolver captchaTypeResolver(CaptchaProperties properties,
                                                   ObjectProvider<DynamicConfigService> dynamicConfigService) {
        return new CaptchaTypeResolver(properties, dynamicConfigService.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(CaptchaService.class)
    public CaptchaService captchaService(CaptchaProperties properties,
                                         CaptchaStore captchaStore,
                                         CaptchaMetrics captchaMetrics,
                                         CaptchaTypeResolver captchaTypeResolver,
                                         ObjectProvider<RateLimiter> rateLimiter,
                                         ObjectProvider<DynamicConfigService> dynamicConfigService) {
        return new CaptchaService(captchaStore, properties, captchaTypeResolver, captchaMetrics,
                rateLimiter.getIfAvailable(), dynamicConfigService.getIfAvailable());
    }
}
