package cn.slainc.boot;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.login.SecurityLoginService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// /auth/login 与 /oauth2/jwks 自批次 9 起由 modality-modules-auth 提供，本控制器只保留验收探针
@RestController
public class Batch4AcceptanceController {

    private final SecurityLoginService loginService;

    private final KickService kickService;

    public Batch4AcceptanceController(SecurityLoginService loginService, KickService kickService) {
        this.loginService = loginService;
        this.kickService = kickService;
    }

    @GetMapping("/batch4/me")
    @RequireLogin
    public Result<Object> me() {
        return Result.ok(loginService.getLoginId(LoginType.TENANT_USER));
    }

    @GetMapping("/batch4/client/me")
    @RequireLogin(LoginType.APP)
    public Result<Object> clientMe() {
        return Result.ok(loginService.getLoginId(LoginType.APP));
    }

    @GetMapping("/batch4/admin/panel")
    @RequireLogin(LoginType.PLATFORM_ADMIN)
    public Result<String> adminPanel() {
        return Result.ok("管理端面板");
    }

    @GetMapping("/batch4/admin/online")
    @RequireLogin(LoginType.PLATFORM_ADMIN)
    public Result<List<Map<String, Object>>> online(@RequestParam String type, @RequestParam Long loginId) {
        return Result.ok(loginService.onlineTerminals(LoginType.of(type), loginId));
    }

    @PostMapping("/batch4/admin/kick")
    @RequireLogin(LoginType.PLATFORM_ADMIN)
    public Result<Void> kick(@RequestParam String type, @RequestParam Long loginId,
                             @RequestParam(defaultValue = "管理员强制下线") String reason) {
        kickService.kickout(LoginType.of(type), loginId, reason);
        return Result.ok();
    }
}
