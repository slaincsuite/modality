package cn.slainc.oauth2.autoconfigure;

import cn.dev33.satoken.oauth2.data.loader.SaOAuth2DataLoader;
import cn.dev33.satoken.oauth2.template.SaOAuth2Template;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.jwt.autoconfigure.JwtAutoConfiguration;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.ClientRecordCache;
import cn.slainc.oauth2.client.ClientRecordRepository;
import cn.slainc.oauth2.client.ClientSecretCipher;
import cn.slainc.oauth2.client.JdbcOauth2DataLoader;
import cn.slainc.oauth2.config.Oauth2Properties;
import cn.slainc.oauth2.consent.ConsentRecordStore;
import cn.slainc.oauth2.consent.ConsentService;
import cn.slainc.oauth2.guard.ClientGuard;
import cn.slainc.oauth2.oidc.IdTokenScopeHandler;
import cn.slainc.oauth2.pkce.PkceDataGenerate;
import cn.slainc.oauth2.pkce.PkceService;
import cn.slainc.oauth2.scope.ScopeRegistry;
import cn.slainc.oauth2.state.StateService;
import cn.slainc.oauth2.template.DynamicOauth2Template;
import cn.slainc.oauth2.userinfo.Oauth2UserInfoService;
import cn.slainc.oauth2.protocol.Oauth2ErrorAdvice;
import cn.slainc.oauth2.protocol.Oauth2ServerDelegate;
import cn.slainc.oauth2.protocol.Oauth2StrategyBinder;
import cn.slainc.redis.ratelimit.RateLimiter;
import cn.slainc.security.autoconfigure.SecurityAutoConfiguration;
import cn.slainc.security.spi.AccountAuthProvider;
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

@AutoConfiguration(after = {SecurityAutoConfiguration.class, JwtAutoConfiguration.class})
@ConditionalOnClass(cn.dev33.satoken.oauth2.template.SaOAuth2Template.class)
@ConditionalOnProperty(prefix = "modality.oauth2", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(Oauth2Properties.class)
public class Oauth2AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ScopeRegistry scopeRegistry(Oauth2Properties properties,
                                       ObjectProvider<DynamicConfigService> configService) {
        return new ScopeRegistry(properties.getScopes(), configService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientSecretCipher clientSecretCipher(Oauth2Properties properties) {
        return new ClientSecretCipher(properties.getClient().getSecretEncryptionKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientRecordCache clientRecordCache(ObjectProvider<ClientRecordRepository> repository,
                                               Oauth2Properties properties) {
        return new ClientRecordCache(repository, properties.getClient().getCacheSeconds());
    }

    @Bean
    @ConditionalOnMissingBean(SaOAuth2DataLoader.class)
    public JdbcOauth2DataLoader oauth2DataLoader(ClientRecordCache cache, ClientSecretCipher cipher,
                                                 ScopeRegistry scopeRegistry, Oauth2Properties properties) {
        return new JdbcOauth2DataLoader(cache, cipher, scopeRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsentService consentService(ObjectProvider<ConsentRecordStore> store,
                                         ObjectProvider<SaOAuth2Template> template) {
        return new ConsentService(store, template);
    }

    @Bean
    @ConditionalOnMissingBean(SaOAuth2Template.class)
    public DynamicOauth2Template oauth2Template(ClientRecordCache cache, ClientSecretCipher cipher,
                                                ObjectProvider<ConsentService> consentService) {
        return new DynamicOauth2Template(cache, cipher, consentService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PkceService pkceService(ObjectProvider<StringRedisTemplate> redisTemplate) {
        return new PkceService(redisTemplate.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(PkceDataGenerate.class)
    public PkceDataGenerate pkceDataGenerate(PkceService pkceService, Oauth2Properties properties) {
        return new PkceDataGenerate(pkceService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public StateService stateService(ObjectProvider<StringRedisTemplate> redisTemplate,
                                     Oauth2Properties properties) {
        return new StateService(redisTemplate.getIfAvailable(), properties.getState().getTimeoutSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public Oauth2UserInfoService oauth2UserInfoService(ObjectProvider<SaOAuth2Template> template,
                                                       ObjectProvider<AccountAuthProvider> accountProvider,
                                                       Oauth2Properties properties) {
        return new Oauth2UserInfoService(template, accountProvider, properties.getLoginType());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientGuard clientGuard(ClientRecordCache cache, ObjectProvider<RateLimiter> rateLimiter,
                                   Oauth2Properties properties) {
        return new ClientGuard(cache, rateLimiter, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientAdminService clientAdminService(ObjectProvider<ClientRecordRepository> repository,
                                                 ClientRecordCache cache, ClientSecretCipher cipher,
                                                 ObjectProvider<DynamicConfigService> configService) {
        return new ClientAdminService(repository, cache, cipher, configService);
    }

    @Bean
    @ConditionalOnMissingBean
    public Oauth2StrategyBinder oauth2StrategyBinder(Oauth2Properties properties, ScopeRegistry scopeRegistry,
                                                     DynamicOauth2Template template, SaOAuth2DataLoader dataLoader,
                                                     ObjectProvider<PkceDataGenerate> pkceDataGenerate,
                                                     ObjectProvider<cn.slainc.security.login.SecurityLoginService> loginService,
                                                     ObjectProvider<cn.slainc.jwt.issue.JwtTokenService> tokenService,
                                                     ObjectProvider<ClientGuard> guard) {
        return new Oauth2StrategyBinder(properties, scopeRegistry, template, dataLoader, pkceDataGenerate,
                loginService, tokenService, guard);
    }

    @Bean
    @ConditionalOnMissingBean
    public Oauth2ServerDelegate oauth2ServerDelegate() {
        return new Oauth2ServerDelegate();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    static class Oauth2WebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public Oauth2ErrorAdvice oauth2ErrorAdvice() {
            return new Oauth2ErrorAdvice();
        }
    }
}
