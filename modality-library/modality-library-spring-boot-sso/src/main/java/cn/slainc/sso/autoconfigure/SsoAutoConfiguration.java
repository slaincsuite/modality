package cn.slainc.sso.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.sso.client.SsoClientAdminService;
import cn.slainc.sso.client.SsoClientRecordCache;
import cn.slainc.sso.client.SsoClientRecordRepository;
import cn.slainc.sso.config.SsoProperties;
import cn.slainc.sso.http.JdkSaHttpTemplate;
import cn.slainc.sso.logout.SsoLogoutService;
import cn.slainc.sso.template.DynamicSsoClientTemplate;
import cn.slainc.sso.template.DynamicSsoServerTemplate;
import cn.slainc.sso.protocol.SsoClientDelegate;
import cn.slainc.sso.protocol.SsoErrorAdvice;
import cn.slainc.sso.protocol.SsoServerDelegate;
import cn.slainc.sso.protocol.SsoStrategyBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration(beforeName = "cn.dev33.satoken.spring.sso.SaSsoBeanRegister")
@ConditionalOnClass(cn.dev33.satoken.sso.template.SaSsoTemplate.class)
@ConditionalOnProperty(prefix = "modality.sso", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SsoProperties.class)
public class SsoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(cn.dev33.satoken.http.SaHttpTemplate.class)
    public JdkSaHttpTemplate saHttpTemplate() {
        return new JdkSaHttpTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    public SsoClientRecordCache ssoClientRecordCache(ObjectProvider<SsoClientRecordRepository> repository,
                                                     SsoProperties properties) {
        return new SsoClientRecordCache(repository, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SsoClientAdminService ssoClientAdminService(ObjectProvider<SsoClientRecordRepository> repository,
                                                       SsoClientRecordCache cache,
                                                       ObjectProvider<DynamicConfigService> configService) {
        return new SsoClientAdminService(repository, cache, configService);
    }

    // 服务端装配要求 secretKey 非空：空密钥下票据签名形同虚设，宁可整体禁用服务端能力
    @Configuration(proxyBeanMethods = false)
    @Conditional(ServerSecretKeyConfiguredCondition.class)
    static class SsoServerConfiguration {

        @Bean
        @ConditionalOnMissingBean(DynamicSsoServerTemplate.class)
        public DynamicSsoServerTemplate ssoServerTemplate(SsoClientRecordCache cache) {
            return new DynamicSsoServerTemplate(cache);
        }

        @Bean
        @ConditionalOnMissingBean
        public SsoLogoutService ssoLogoutService(DynamicSsoServerTemplate template) {
            return new SsoLogoutService(template);
        }

        @Bean
        @ConditionalOnMissingBean
        public SsoServerDelegate ssoServerDelegate(DynamicSsoServerTemplate template) {
            return new SsoServerDelegate(template);
        }
    }

    @Bean
    @ConditionalOnMissingBean(DynamicSsoClientTemplate.class)
    public DynamicSsoClientTemplate ssoClientTemplate(SsoProperties properties) {
        return new DynamicSsoClientTemplate(buildClientConfig(properties));
    }

    @Bean
    @ConditionalOnMissingBean
    public SsoClientDelegate ssoClientDelegate(DynamicSsoClientTemplate template) {
        return new SsoClientDelegate(template);
    }

    @Bean
    @ConditionalOnMissingBean
    public SsoStrategyBinder ssoStrategyBinder(SsoProperties properties,
                                               ObjectProvider<DynamicSsoServerTemplate> serverTemplate,
                                               DynamicSsoClientTemplate clientTemplate,
                                               ObjectProvider<cn.slainc.security.login.SecurityLoginService> loginService) {
        return new SsoStrategyBinder(properties, serverTemplate.getIfAvailable(), clientTemplate, loginService);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    static class SsoWebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SsoErrorAdvice ssoErrorAdvice() {
            return new SsoErrorAdvice();
        }
    }

    private static cn.dev33.satoken.sso.config.SaSsoClientConfig buildClientConfig(SsoProperties properties) {
        SsoProperties.Client client = properties.getClient();
        cn.dev33.satoken.sso.config.SaSsoClientConfig config = new cn.dev33.satoken.sso.config.SaSsoClientConfig();
        config.setClient(client.getClient());
        config.setServerUrl(client.getServerUrl());
        config.setSecretKey(client.getSecretKey());
        config.setIsSlo(client.isSlo());
        config.setIsCheckSign(client.isCheckSign());
        config.setRegLogoutCall(client.isRegLogoutCall());
        config.setIsHttp(true);
        return config;
    }
}
