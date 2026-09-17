package cn.slainc.sign.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.sign.aspect.SignAspect;
import cn.slainc.sign.composer.SignComposer;
import cn.slainc.sign.config.SignProperties;
import cn.slainc.sign.key.SignKeyRegistry;
import cn.slainc.sign.store.MemorySignNonceStore;
import cn.slainc.sign.store.RedisSignNonceStore;
import cn.slainc.sign.store.SignNonceStore;
import cn.slainc.sign.tool.SignTool;
import cn.slainc.sign.verify.SignVerifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.sign", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SignProperties.class)
public class SignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SignKeyRegistry signKeyRegistry(SignProperties properties,
                                           ObjectProvider<DynamicConfigService> configService) {
        return new SignKeyRegistry(properties, configService);
    }

    @Bean
    @ConditionalOnMissingBean
    public SignComposer signComposer(SignProperties properties) {
        return new SignComposer(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SignNonceStore signNonceStore(ObjectProvider<StringRedisTemplate> redisTemplate) {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        return template == null ? new MemorySignNonceStore() : new RedisSignNonceStore(template);
    }

    @Bean
    @ConditionalOnMissingBean
    public SignVerifier signVerifier(SignKeyRegistry signKeyRegistry, SignComposer signComposer,
                                     SignProperties properties, SignNonceStore signNonceStore) {
        return new SignVerifier(signKeyRegistry, signComposer, properties, signNonceStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public SignTool signTool(SignComposer signComposer) {
        return new SignTool(signComposer);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    static class SignWebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SignAspect signAspect(SignVerifier signVerifier, SignProperties properties) {
            return new SignAspect(signVerifier, properties);
        }
    }
}
