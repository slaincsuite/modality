package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.login.SecurityLoginService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthDemoController {

    private final SecurityLoginService loginService;

    public AuthDemoController(SecurityLoginService loginService) {
        this.loginService = loginService;
    }

    public record LoginBody(String username, String password, String deviceType, String deviceId, String clientIp) {
    }

    @PostMapping("/auth/login")
    public Result<LoginResult> login(@RequestHeader(value = "X-Login-Type", required = false) String loginType,
                                     @RequestBody LoginBody body) {
        LoginType type = LoginType.of(loginType) == null ? LoginType.TENANT_USER : LoginType.of(loginType);
        return Result.ok(loginService.login(type, new LoginRequest(body.username(), body.password(),
                body.deviceType(), body.deviceId(), body.clientIp())));
    }

    @PostMapping("/auth/logout")
    public Result<Void> logout(@RequestHeader(value = "X-Login-Type", required = false) String loginType) {
        loginService.logout(LoginType.of(loginType) == null ? LoginType.TENANT_USER : LoginType.of(loginType));
        return Result.ok();
    }
}
