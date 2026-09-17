package cn.slainc.boot;

import cn.dev33.satoken.sso.util.SaSsoConsts;
import cn.dev33.satoken.apikey.annotation.SaCheckApiKey;
import cn.slainc.core.result.Result;
import cn.slainc.oauth2.web.Oauth2ServerDelegate;
import cn.slainc.security.StpKit;
import cn.slainc.sign.annotation.VerifySign;
import cn.slainc.sso.web.SsoClientDelegate;
import cn.slainc.sso.web.SsoServerDelegate;
import cn.slainc.web.result.NoWrap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Batch5AcceptanceController {

    private final Oauth2ServerDelegate oauth2Delegate;

    private final SsoServerDelegate ssoServerDelegate;

    private final SsoClientDelegate ssoClientDelegate;

    public Batch5AcceptanceController(Oauth2ServerDelegate oauth2Delegate,
                                      SsoServerDelegate ssoServerDelegate,
                                      SsoClientDelegate ssoClientDelegate) {
        this.oauth2Delegate = oauth2Delegate;
        this.ssoServerDelegate = ssoServerDelegate;
        this.ssoClientDelegate = ssoClientDelegate;
    }

    @NoWrap
    @RequestMapping("/oauth2/**")
    public Object oauth2() {
        return oauth2Delegate.handle();
    }

    @NoWrap
    @RequestMapping("/sso/**")
    public Object sso() {
        Object result = ssoServerDelegate.handle();
        if (SaSsoConsts.NOT_HANDLE.equals(result)) {
            result = ssoClientDelegate.handle();
        }
        return result;
    }

    @NoWrap
    @GetMapping("/sso/isLogin")
    public Object isLogin() {
        return StpKit.client().isLogin();
    }

    @SaCheckApiKey(scope = "order:read")
    @GetMapping("/batch5/api/order")
    public Result<String> order(@RequestParam(defaultValue = "1") String orderId) {
        return Result.ok("订单 " + orderId + " 读取成功");
    }

    @VerifySign(appId = "boot-app")
    @GetMapping("/batch5/sign/order")
    public Result<String> signedOrder(@RequestParam(defaultValue = "1") String orderId) {
        return Result.ok("签名订单 " + orderId + " 处理成功");
    }
}
