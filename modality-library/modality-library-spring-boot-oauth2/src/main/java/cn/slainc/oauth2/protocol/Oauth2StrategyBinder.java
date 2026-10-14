package cn.slainc.oauth2.protocol;

import cn.dev33.satoken.oauth2.SaOAuth2Manager;
import cn.dev33.satoken.oauth2.config.SaOAuth2OidcConfig;
import cn.dev33.satoken.oauth2.config.SaOAuth2ServerConfig;
import cn.dev33.satoken.oauth2.data.loader.SaOAuth2DataLoader;
import cn.dev33.satoken.oauth2.processor.SaOAuth2ServerProcessor;
import cn.dev33.satoken.oauth2.strategy.SaOAuth2Strategy;
import cn.dev33.satoken.util.SaResult;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.oauth2.config.Oauth2Properties;
import cn.slainc.oauth2.guard.ClientGuard;
import cn.slainc.oauth2.oidc.IdTokenScopeHandler;
import cn.slainc.oauth2.pkce.PkceDataGenerate;
import cn.slainc.oauth2.scope.ScopeRegistry;
import cn.slainc.oauth2.template.DynamicOauth2Template;
import cn.slainc.security.LoginTypes;
import cn.slainc.security.StpKit;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.StringJoiner;

public class Oauth2StrategyBinder implements SmartInitializingSingleton {

    private final Oauth2Properties properties;

    private final ScopeRegistry scopeRegistry;

    private final DynamicOauth2Template template;

    private final SaOAuth2DataLoader dataLoader;

    private final ObjectProvider<PkceDataGenerate> pkceDataGenerate;

    private final ObjectProvider<SecurityLoginService> loginService;

    private final ObjectProvider<JwtTokenService> tokenService;

    private final ObjectProvider<ClientGuard> guard;

    public Oauth2StrategyBinder(Oauth2Properties properties, ScopeRegistry scopeRegistry,
                                DynamicOauth2Template template, SaOAuth2DataLoader dataLoader,
                                ObjectProvider<PkceDataGenerate> pkceDataGenerate,
                                ObjectProvider<SecurityLoginService> loginService,
                                ObjectProvider<JwtTokenService> tokenService,
                                ObjectProvider<ClientGuard> guard) {
        this.properties = properties;
        this.scopeRegistry = scopeRegistry;
        this.template = template;
        this.dataLoader = dataLoader;
        this.pkceDataGenerate = pkceDataGenerate;
        this.loginService = loginService;
        this.tokenService = tokenService;
        this.guard = guard;
    }

    @Override
    public void afterSingletonsInstantiated() {
        SaOAuth2Manager.setStpLogic(StpKit.of(LoginTypes.resolve(properties.getLoginType())));
        SaOAuth2Manager.setServerConfig(buildServerConfig());
        SaOAuth2Manager.setDataLoader(dataLoader);
        SaOAuth2Manager.setTemplate(template);
        PkceDataGenerate generate = pkceDataGenerate.getIfAvailable();
        if (generate != null) {
            SaOAuth2Manager.setDataGenerate(generate);
        }

        SaOAuth2Strategy strategy = SaOAuth2Strategy.instance;
        strategy.registerDefaultScopeHandler();
        strategy.registerGrantTypeHandler(new cn.slainc.oauth2.grant.ClientCredentialsGrantTypeHandler());
        JwtTokenService jwtTokenService = tokenService.getIfAvailable();
        if (jwtTokenService != null && properties.getOidc().isEnabled()) {
            strategy.registerScopeHandler(new IdTokenScopeHandler(jwtTokenService, properties,
                    IdTokenScopeHandler.SCOPE_OPENID));
            strategy.registerScopeHandler(new IdTokenScopeHandler(jwtTokenService, properties,
                    IdTokenScopeHandler.SCOPE_OIDC));
        }
        strategy.doLoginHandle = (name, pwd) -> {
            SecurityLoginService service = loginService.getIfAvailable();
            if (service == null) {
                return SaResult.error("未接入登录服务");
            }
            service.login(LoginTypes.resolve(properties.getLoginType()), LoginRequest.of(name, pwd));
            return SaResult.ok("登录成功");
        };
        strategy.notLoginView = () -> JsonUtils.toJson(Result.fail(ResultCode.UNAUTHORIZED.code(), "尚未登录，请先完成登录"));
        strategy.confirmView = (clientId, scopes) ->
                JsonUtils.toJson(ConfirmView.of(clientId, scopes, scopeRegistry));
        ClientGuard clientGuard = guard.getIfAvailable();
        if (clientGuard != null && properties.getGuard().isEnabled()) {
            strategy.userAuthorizeClientCheck = clientGuard::check;
        }
    }

    private SaOAuth2ServerConfig buildServerConfig() {
        SaOAuth2ServerConfig config = new SaOAuth2ServerConfig();
        config.setEnableAuthorizationCode(true);
        config.setEnableImplicit(true);
        config.setEnablePassword(true);
        config.setEnableClientCredentials(true);
        config.setIsNewRefresh(properties.isNewRefresh());
        config.setCodeTimeout(properties.getCodeTimeoutSeconds());
        config.setAccessTokenTimeout(properties.getAccessTokenTimeoutSeconds());
        config.setRefreshTokenTimeout(properties.getRefreshTokenTimeoutSeconds());
        config.setClientTokenTimeout(properties.getClientTokenTimeoutSeconds());
        config.setMaxAccessTokenCount(-1);
        config.setMaxRefreshTokenCount(-1);
        config.setMaxClientTokenCount(-1);
        config.setHigherScope(join(scopeRegistry.higherScopeNames()));
        config.setLowerScope(join(scopeRegistry.lowerScopeNames()));
        SaOAuth2OidcConfig oidcConfig = new SaOAuth2OidcConfig();
        oidcConfig.iss = properties.getOidc().getIssuer();
        config.setOidc(oidcConfig);
        return config;
    }

    private static String join(java.util.List<String> values) {
        StringJoiner joiner = new StringJoiner(",");
        if (values != null) {
            values.forEach(joiner::add);
        }
        return joiner.toString();
    }
}
