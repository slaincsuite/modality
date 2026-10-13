package cn.slainc.modules.auth.web;
import cn.slainc.modules.auth.context.CurrentLogin;

import cn.slainc.modules.auth.service.AuthLoginService;

import cn.slainc.core.result.Result;
import cn.slainc.security.StpKit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "登录", description = "账号密码登录与验证码校验")
@RestController
public class AuthLoginController {

    private final AuthLoginService loginService;

    public AuthLoginController(AuthLoginService loginService) {
        this.loginService = loginService;
    }

    public record LoginBody(String username, String password, String deviceType, String deviceId,
                            String captchaId, String captchaCode, Boolean remember) {
    }

    @PostMapping("/auth/login")
    public Result<AuthLoginService.AuthLoginView> login(
            @RequestHeader(value = CurrentLogin.TYPE_HEADER, required = false) String type,
            @RequestHeader(value = "X-Device-Id", required = false) String headerDeviceId,
            @RequestBody LoginBody body) {
        String deviceId = body.deviceId() != null && !body.deviceId().isBlank() ? body.deviceId() : headerDeviceId;
        boolean remember = Boolean.TRUE.equals(body.remember());
        var command = new AuthLoginService.LoginCommand(body.username(), body.password(), body.deviceType(),
                deviceId, body.captchaId(), body.captchaCode(), remember);
        return Result.ok(loginService.login(CurrentLogin.type(type), command));
    }

    @PostMapping("/auth/token/keepalive")
    public Result<Long> keepalive(HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        return Result.ok(StpKit.of(CurrentLogin.type(request)).getTokenTimeout());
    }
}
