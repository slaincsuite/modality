package cn.slainc.apikey.autoconfigure;

import cn.dev33.satoken.apikey.loader.SaApiKeyDataLoader;
import cn.dev33.satoken.apikey.template.SaApiKeyTemplate;
import cn.slainc.apikey.config.ApiKeyProperties;
import cn.slainc.apikey.guard.ApiKeyGuard;
import cn.slainc.apikey.guard.ApiKeyStatsRecorder;
import cn.slainc.apikey.service.ApiKeyStatsService;
import cn.slainc.apikey.template.GuardedApiKeyTemplate;
import cn.slainc.apikey.web.ApiKeyErrorAdvice;
import cn.slainc.redis.ratelimit.RateLimiter;
import cn.slainc.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = SecurityAutoConfiguration.class)
@ConditionalOnClass(SaApiKeyTemplate.class)
@ConditionalOnProperty(prefix = "modality.apikey", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SaApiKeyTemplate.class)
    public GuardedApiKeyTemplate apiKeyTemplate(ApiKeyProperties properties, ObjectProvider<ApiKeyGuard> guard) {
        return new GuardedApiKeyTemplate(properties.getNamespace(), properties.getHeader(),
                guard.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.apikey.guard", name = "enabled", matchIfMissing = true)
    public ApiKeyGuard apiKeyGuard(ObjectProvider<RateLimiter> rateLimiter,
                                   ObjectProvider<ApiKeyStatsRecorder> statsRecorder,
                                   ApiKeyProperties properties) {
        return new ApiKeyGuard(rateLimiter, statsRecorder.getIfAvailable(), properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.apikey.stats", name = "enabled", matchIfMissing = true)
    public ApiKeyStatsRecorder apiKeyStatsRecorder(ObjectProvider<StringRedisTemplate> redisTemplate) {
        return new ApiKeyStatsRecorder(redisTemplate.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyStatsService apiKeyStatsService(ObjectProvider<StringRedisTemplate> redisTemplate) {
        return new ApiKeyStatsService(redisTemplate.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(ApiKeyManagerBinder.class)
    public ApiKeyManagerBinder apiKeyManagerBinder(ObjectProvider<SaApiKeyTemplate> template,
                                                   ObjectProvider<SaApiKeyDataLoader> dataLoader,
                                                   ApiKeyProperties properties) {
        return new ApiKeyManagerBinder(template.getIfAvailable(), dataLoader, properties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    static class ApiKeyWebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ApiKeyErrorAdvice apiKeyErrorAdvice() {
            return new ApiKeyErrorAdvice();
        }
    }
}
