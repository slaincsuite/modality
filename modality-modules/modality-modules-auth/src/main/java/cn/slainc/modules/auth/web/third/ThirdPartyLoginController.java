package cn.slainc.modules.auth.web.third;

import cn.slainc.modules.auth.service.third.ThirdPartyLoginService;

import cn.slainc.modules.auth.service.qrcode.QrLoginService;

import cn.slainc.core.result.Result;
import cn.slainc.modules.auth.context.CurrentLogin;
import cn.slainc.security.StpKit;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "第三方登录", description = "第三方平台登录与回调")
@RestController
@RequestMapping("/auth/third")
public class ThirdPartyLoginController {

    private final ThirdPartyLoginService loginService;

    private final ThirdAuthConfigRegistry configRegistry;

    public ThirdPartyLoginController(ThirdPartyLoginService loginService, ThirdAuthConfigRegistry configRegistry) {
        this.loginService = loginService;
        this.configRegistry = configRegistry;
    }

    public record BindBody(String bindTicketId, String username, String password, String deviceType,
                           String deviceId) {
    }

    public record PlatformView(String code, String label) {
    }

    @GetMapping("/platforms")
    public Result<List<PlatformView>> platforms() {
        List<PlatformView> views = new ArrayList<>();
        for (ThirdPartyPlatform platform : ThirdPartyPlatform.values()) {
            if (configRegistry.enabled(platform)) {
                views.add(new PlatformView(platform.code(), platform.label()));
            }
        }
        return Result.ok(views);
    }

    @GetMapping("/{platform}/authorize-url")
    public Result<Map<String, String>> authorizeUrl(@PathVariable String platform) {
        var context = loginService.authorizeUrl(platform);
        return Result.ok(Map.of("authorizeUrl", context.authorizeUrl(), "state", context.state()));
    }

    @RequestMapping("/callback/{platform}")
    public Result<ThirdPartyLoginService.ThirdLoginView> callback(@PathVariable String platform,
                                                                  HttpServletRequest request,
                                                                  @RequestParam Map<String, String> params) {
        Map<String, String> merged = new java.util.HashMap<>(params);
        request.getParameterMap().forEach((name, values) -> {
            if (values != null && values.length > 0) {
                merged.putIfAbsent(name, values[0]);
            }
        });
        return Result.ok(loginService.callback(platform, merged));
    }

    @PostMapping("/bind")
    public Result<Object> bind(@org.springframework.web.bind.annotation.RequestHeader(
            value = CurrentLogin.TYPE_HEADER, required = false) String type, @RequestBody BindBody body) {
        return Result.ok(loginService.bindWithPassword(body.bindTicketId(), CurrentLogin.type(type),
                body.username(), body.password(), body.deviceType(), body.deviceId()));
    }

    @PostMapping("/bind-current")
    public Result<Void> bindCurrent(@RequestBody BindBody body, HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var loginType = CurrentLogin.type(request);
        loginService.bindCurrent(body.bindTicketId(), loginType, StpKit.of(loginType).getLoginId());
        return Result.ok();
    }

    @GetMapping("/bindings")
    public Result<List<Object>> bindings(HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var loginType = CurrentLogin.type(request);
        return Result.ok(loginService.bindings(StpKit.of(loginType).getLoginId()).stream()
                .map(binding -> (Object) binding).toList());
    }

    @DeleteMapping("/bindings/{platform}/{openId}")
    public Result<Void> unbind(@PathVariable String platform, @PathVariable String openId,
                               HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var loginType = CurrentLogin.type(request);
        loginService.unbind(platform, openId, StpKit.of(loginType).getLoginId());
        return Result.ok();
    }
}
