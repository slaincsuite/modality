package cn.slainc.modules.auth.autoconfigure;

import cn.dev33.satoken.oauth2.template.SaOAuth2Template;
import cn.slainc.jwt.introspect.IntrospectionService;
import cn.slainc.jwt.jwks.JwksService;
import cn.slainc.jwt.refresh.RefreshTokenService;
import cn.slainc.jwt.revoke.RevocationService;
import cn.slainc.jwt.verify.JwtVerifier;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.auth.web.token.JwksController;
import cn.slainc.modules.auth.web.oauth2.OAuth2ProtocolController;
import cn.slainc.modules.auth.web.oauth2.OAuth2ConsentController;
import cn.slainc.modules.auth.web.oauth2.OidcUserInfoController;
import cn.slainc.modules.auth.web.sso.SsoProtocolController;
import cn.slainc.modules.auth.web.token.AuthTokenController;
import cn.slainc.modules.auth.service.token.AuthTokenService;
import cn.slainc.modules.auth.service.token.TokenClientAuthenticator;
import cn.slainc.modules.auth.service.token.TokenEndpointGuard;
import cn.slainc.oauth2.userinfo.Oauth2UserInfoService;
import cn.slainc.oauth2.protocol.Oauth2ServerDelegate;
import cn.slainc.redis.ratelimit.RateLimiter;
import cn.slainc.sso.protocol.SsoServerDelegate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@AutoConfiguration(afterName = {"cn.slainc.oauth2.autoconfigure.Oauth2AutoConfiguration",
        "cn.slainc.sso.autoconfigure.SsoAutoConfiguration", "cn.slainc.jwt.autoconfigure.JwtAutoConfiguration",
        "cn.slainc.security.autoconfigure.SecurityAutoConfiguration"})
@EnableConfigurationProperties(AuthProperties.class)
public class AuthProtocolAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(Oauth2ServerDelegate.class)
    @Import({OAuth2ProtocolController.class, OAuth2ConsentController.class})
    static class Oauth2Endpoints {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(JwksService.class)
    @Import(JwksController.class)
    static class JwksEndpoints {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(Oauth2UserInfoService.class)
    @Import(OidcUserInfoController.class)
    static class UserInfoEndpoints {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(SsoServerDelegate.class)
    @Import(SsoProtocolController.class)
    static class SsoEndpoints {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(JwtVerifier.class)
    @Import(AuthTokenController.class)
    static class TokenEndpoints {

        @Bean
        @ConditionalOnMissingBean
        public TokenEndpointGuard tokenEndpointGuard(AuthProperties properties,
                                                     ObjectProvider<RateLimiter> rateLimiter) {
            return new TokenEndpointGuard(properties, rateLimiter);
        }

        @Bean
        @ConditionalOnMissingBean
        public TokenClientAuthenticator tokenClientAuthenticator(ObjectProvider<SaOAuth2Template> template) {
            return new TokenClientAuthenticator(template);
        }

        @Bean
        @ConditionalOnMissingBean
        public AuthTokenService authTokenService(JwtVerifier verifier, RefreshTokenService refreshService,
                                                 RevocationService revocationService,
                                                 IntrospectionService introspectionService,
                                                 TokenClientAuthenticator clientAuthenticator,
                                                 TokenEndpointGuard endpointGuard) {
            return new AuthTokenService(verifier, refreshService, revocationService, introspectionService,
                    clientAuthenticator, endpointGuard);
        }
    }
}
