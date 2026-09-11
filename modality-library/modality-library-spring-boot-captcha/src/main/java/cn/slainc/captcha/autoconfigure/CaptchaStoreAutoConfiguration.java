package cn.slainc.captcha.autoconfigure;

import cn.slainc.captcha.store.CaptchaStore;
import cn.slainc.captcha.store.MemoryCaptchaStore;
import cn.slainc.captcha.store.RedisCaptchaStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
public class CaptchaStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "modality.captcha", name = "store",
            havingValue = "redis", matchIfMissing = true)
    @ConditionalOnMissingBean(CaptchaStore.class)
    public RedisCaptchaStore redisCaptchaStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisCaptchaStore(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "modality.captcha", name = "store", havingValue = "memory")
    @ConditionalOnMissingBean(CaptchaStore.class)
    public MemoryCaptchaStore memoryCaptchaStore() {
        return new MemoryCaptchaStore();
    }

    @Bean
    @ConditionalOnMissingBean(CaptchaStore.class)
    public MemoryCaptchaStore fallbackCaptchaStore() {
        return new MemoryCaptchaStore();
    }
}
