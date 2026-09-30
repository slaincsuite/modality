package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.auth.service.AuthSessionService;
import cn.slainc.modules.auth.store.MemoryThirdBindTicketStore;
import cn.slainc.modules.auth.store.RedisThirdBindTicketStore;
import cn.slainc.modules.auth.store.ThirdBindTicketStore;
import cn.slainc.modules.auth.web.ThirdPartyLoginController;
import cn.slainc.modules.auth.service.ThirdPartyLoginService;
import cn.slainc.security.lock.AccountLockService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.sensitive.xss.XssSanitizer;
import cn.slainc.thirdauth.ThirdAuthService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(afterName = "cn.slainc.thirdauth.autoconfigure.ThirdAuthAutoConfiguration")
@ConditionalOnClass(ThirdAuthService.class)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthThirdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ThirdBindTicketStore thirdBindTicketStore(ObjectProvider<StringRedisTemplate> redis) {
        StringRedisTemplate template = redis.getIfAvailable();
        return template == null ? new MemoryThirdBindTicketStore() : new RedisThirdBindTicketStore(template);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(ThirdAuthService.class)
    @Import(ThirdPartyLoginController.class)
    static class ThirdEndpoints {

        @Bean
        @ConditionalOnMissingBean
        public ThirdPartyLoginService thirdPartyLoginService(ThirdAuthService thirdAuthService,
                                                             ThirdBindTicketStore bindStore,
                                                             AuthProperties properties,
                                                             AuthSessionService sessionService,
                                                             ObjectProvider<AccountAuthProvider> authProvider,
                                                             ObjectProvider<AccountLockService> lockService,
                                                             ObjectProvider<XssSanitizer> sanitizer) {
            return new ThirdPartyLoginService(thirdAuthService, bindStore, properties, sessionService, authProvider,
                    lockService, sanitizer);
        }
    }
}
