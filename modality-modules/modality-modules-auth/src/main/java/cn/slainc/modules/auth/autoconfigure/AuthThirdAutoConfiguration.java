package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.auth.service.session.AuthSessionService;
import cn.slainc.modules.auth.service.qrcode.QrImageService;
import cn.slainc.modules.auth.store.AuthTicketStore;
import cn.slainc.modules.auth.third.ThirdBindTicket;
import cn.slainc.modules.auth.web.third.ThirdPartyLoginController;
import cn.slainc.modules.auth.service.third.ThirdPartyLoginService;
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
    // 返回类型同为 AuthTicketStore，按 bean 名判重避免与扫码票据存储相互误退避
    @ConditionalOnMissingBean(name = "thirdBindTicketStore")
    public AuthTicketStore<ThirdBindTicket> thirdBindTicketStore(ObjectProvider<StringRedisTemplate> redis) {
        return AuthTicketStore.of(redis.getIfAvailable(), AuthTicketStore.BIND_KEY_PREFIX,
                ThirdBindTicket::getTicketId, ThirdBindTicket.class);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(ThirdAuthService.class)
    @Import(ThirdPartyLoginController.class)
    static class ThirdEndpoints {

        @Bean
        @ConditionalOnMissingBean
        public ThirdPartyLoginService thirdPartyLoginService(ThirdAuthService thirdAuthService,
                                                             AuthTicketStore<ThirdBindTicket> bindStore,
                                                             AuthProperties properties,
                                                             AuthSessionService sessionService,
                                                             ObjectProvider<AccountAuthProvider> authProvider,
                                                             ObjectProvider<AccountLockService> lockService,
                                                             ObjectProvider<XssSanitizer> sanitizer,
                                                             ObjectProvider<QrImageService> qrImageService) {
            return new ThirdPartyLoginService(thirdAuthService, bindStore, properties, sessionService, authProvider,
                    lockService, sanitizer, qrImageService);
        }
    }
}
