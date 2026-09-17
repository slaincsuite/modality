package cn.slainc.sso.web;

import cn.dev33.satoken.sso.SaSsoManager;
import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.config.SaSsoServerConfig;
import cn.dev33.satoken.sso.strategy.SaSsoServerStrategy;
import cn.dev33.satoken.util.SaResult;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.security.LoginTypes;
import cn.slainc.security.StpKit;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.sso.config.SsoProperties;
import cn.slainc.sso.template.DynamicSsoClientTemplate;
import cn.slainc.sso.template.DynamicSsoServerTemplate;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class SsoStrategyBinder implements SmartInitializingSingleton {

    private final SsoProperties properties;

    private final DynamicSsoServerTemplate serverTemplate;

    private final DynamicSsoClientTemplate clientTemplate;

    private final ObjectProvider<SecurityLoginService> loginService;

    public SsoStrategyBinder(SsoProperties properties, DynamicSsoServerTemplate serverTemplate,
                             DynamicSsoClientTemplate clientTemplate,
                             ObjectProvider<SecurityLoginService> loginService) {
        this.properties = properties;
        this.serverTemplate = serverTemplate;
        this.clientTemplate = clientTemplate;
        this.loginService = loginService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        serverTemplate.setStpLogic(StpKit.of(LoginTypes.resolve(properties.getLoginType())));
        SaSsoManager.setServerConfig(buildServerConfig());

        SaSsoServerStrategy strategy = serverTemplate.strategy;
        strategy.doLoginHandle = (name, pwd) -> {
            SecurityLoginService service = loginService.getIfAvailable();
            if (service == null) {
                return SaResult.error("未接入登录服务");
            }
            service.login(LoginTypes.resolve(properties.getLoginType()), LoginRequest.of(name, pwd));
            return SaResult.ok("登录成功");
        };
        strategy.notLoginView = () -> JsonUtils.toJson(
                Result.fail(ResultCode.UNAUTHORIZED.code(), "尚未登录认证中心，请先登录"));

        if (properties.getClient().isEnabled()) {
            String clientLoginType = properties.getClient().getLoginType();
            if (clientLoginType == null || clientLoginType.isBlank()) {
                clientLoginType = properties.getLoginType();
            }
            clientTemplate.setStpLogic(StpKit.of(LoginTypes.resolve(clientLoginType)));
            SaSsoManager.setClientConfig(clientTemplate.getClientConfig());
        }
    }

    private SaSsoServerConfig buildServerConfig() {
        SsoProperties.Server server = properties.getServer();
        SaSsoServerConfig config = new SaSsoServerConfig();
        config.setTicketTimeout(server.getTicketTimeoutSeconds());
        config.setSecretKey(server.getSecretKey());
        config.setAllowUrl(server.getAllowUrl());
        config.setIsSlo(server.isSlo());
        config.setIsCheckSign(server.isCheckSign());
        config.setAllowAnonClient(server.isAllowAnonClient());
        config.setHomeRoute(server.getHomeRoute());
        return config;
    }
}
