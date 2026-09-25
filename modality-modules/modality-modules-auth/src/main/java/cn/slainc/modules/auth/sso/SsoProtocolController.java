package cn.slainc.modules.auth.sso;

import cn.dev33.satoken.sso.util.SaSsoConsts;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.LoginTypes;
import cn.slainc.security.StpKit;
import cn.slainc.sso.config.SsoProperties;
import cn.slainc.sso.web.SsoClientDelegate;
import cn.slainc.sso.web.SsoServerDelegate;
import cn.slainc.web.result.NoWrap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SsoProtocolController {

    private final SsoServerDelegate ssoServerDelegate;

    private final SsoClientDelegate ssoClientDelegate;

    private final SsoProperties ssoProperties;

    public SsoProtocolController(SsoServerDelegate ssoServerDelegate,
                                 ObjectProvider<SsoClientDelegate> clientDelegate,
                                 SsoProperties ssoProperties) {
        this.ssoServerDelegate = ssoServerDelegate;
        this.ssoClientDelegate = clientDelegate.getIfAvailable();
        this.ssoProperties = ssoProperties;
    }

    @NoWrap
    @RequestMapping("/sso/**")
    public Object sso() {
        Object result = ssoServerDelegate.handle();
        if (SaSsoConsts.NOT_HANDLE.equals(result) && ssoProperties.getClient().isEnabled()
                && ssoClientDelegate != null) {
            result = ssoClientDelegate.handle();
        }
        return result;
    }

    @NoWrap
    @GetMapping("/sso/isLogin")
    public Object isLogin() {
        String configured = ssoProperties.getClient().getLoginType();
        LoginType type = LoginTypes.resolveOrDefault(
                configured == null || configured.isBlank() ? LoginType.APP.value() : configured,
                LoginType.APP.value());
        return StpKit.of(type).isLogin();
    }
}
