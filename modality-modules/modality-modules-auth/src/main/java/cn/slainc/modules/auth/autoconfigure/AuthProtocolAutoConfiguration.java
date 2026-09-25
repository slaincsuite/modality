package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.jwt.introspect.IntrospectionService;
import cn.slainc.jwt.jwks.JwksService;
import cn.slainc.jwt.refresh.RefreshTokenService;
import cn.slainc.jwt.revoke.RevocationService;
import cn.slainc.jwt.verify.JwtVerifier;
import cn.slainc.modules.auth.oauth2.JwksController;
import cn.slainc.modules.auth.oauth2.OAuth2ProtocolController;
import cn.slainc.modules.auth.oauth2.OAuth2ConsentController;
import cn.slainc.modules.auth.oauth2.OidcUserInfoController;
import cn.slainc.modules.auth.sso.SsoProtocolController;
import cn.slainc.modules.auth.token.AuthTokenController;
import cn.slainc.modules.auth.token.AuthTokenService;
import cn.slainc.oauth2.userinfo.Oauth2UserInfoService;
import cn.slainc.oauth2.web.Oauth2ServerDelegate;
import cn.slainc.sso.web.SsoServerDelegate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@AutoConfiguration(afterName = {"cn.slainc.oauth2.autoconfigure.Oauth2AutoConfiguration",
        "cn.slainc.sso.autoconfigure.SsoAutoConfiguration", "cn.slainc.jwt.autoconfigure.JwtAutoConfiguration",
        "cn.slainc.security.autoconfigure.SecurityAutoConfiguration"})
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
        public AuthTokenService authTokenService(JwtVerifier verifier, RefreshTokenService refreshService,
                                                 RevocationService revocationService,
                                                 IntrospectionService introspectionService) {
            return new AuthTokenService(verifier, refreshService, revocationService, introspectionService);
        }
    }
}
