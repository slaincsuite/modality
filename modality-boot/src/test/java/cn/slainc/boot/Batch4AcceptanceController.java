package cn.slainc.boot;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.jwt.jwks.JwksService;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.web.result.NoWrap;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class Batch4AcceptanceController {

    private final SecurityLoginService loginService;

    private final KickService kickService;

    private final JwksService jwksService;

    public Batch4AcceptanceController(SecurityLoginService loginService, KickService kickService,
                                      JwksService jwksService) {
        this.loginService = loginService;
        this.kickService = kickService;
        this.jwksService = jwksService;
    }

    public record LoginBody(String username, String password, String deviceType, String deviceId) {
    }

    @PostMapping("/auth/login")
    public Result<LoginResult> login(@RequestHeader(value = "X-Login-Type", required = false) String type,
                                     @RequestBody LoginBody body) {
        LoginType loginType = LoginType.of(type) == null ? LoginType.USER : LoginType.of(type);
        return Result.ok(loginService.login(loginType, new LoginRequest(body.username(), body.password(),
                body.deviceType(), body.deviceId(), null)));
    }

    // 端点由应用（或业务模块）自己声明：库只提供 JwksService，平台主源码里不放任何功能性控制器
    @GetMapping(value = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    @NoWrap
    public String jwks() {
        return jwksService.getJwksJson();
    }

    @GetMapping("/batch4/me")
    @RequireLogin
    public Result<Object> me() {
        return Result.ok(loginService.getLoginId(LoginType.USER));
    }

    @GetMapping("/batch4/client/me")
    @RequireLogin(LoginType.CLIENT)
    public Result<Object> clientMe() {
        return Result.ok(loginService.getLoginId(LoginType.CLIENT));
    }

    @GetMapping("/batch4/admin/panel")
    @RequireLogin(LoginType.ADMIN)
    public Result<String> adminPanel() {
        return Result.ok("管理端面板");
    }

    @GetMapping("/batch4/admin/online")
    @RequireLogin(LoginType.ADMIN)
    public Result<List<Map<String, Object>>> online(@RequestParam String type, @RequestParam Long loginId) {
        return Result.ok(loginService.onlineTerminals(LoginType.of(type), loginId));
    }

    @PostMapping("/batch4/admin/kick")
    @RequireLogin(LoginType.ADMIN)
    public Result<Void> kick(@RequestParam String type, @RequestParam Long loginId,
                             @RequestParam(defaultValue = "管理员强制下线") String reason) {
        kickService.kickout(LoginType.of(type), loginId, reason);
        return Result.ok();
    }
}
