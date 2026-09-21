package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.security.login.SecurityLoginService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProtectedDemoController {

    private final SecurityLoginService loginService;

    public ProtectedDemoController(SecurityLoginService loginService) {
        this.loginService = loginService;
    }

    @GetMapping("/demo/user/me")
    @RequireLogin(LoginType.TENANT_USER)
    public Result<String> currentUser() {
        return Result.ok(String.valueOf(loginService.getLoginId(LoginType.TENANT_USER)));
    }

    @GetMapping("/demo/admin/panel")
    @RequireLogin(LoginType.PLATFORM_ADMIN)
    public Result<String> adminPanel() {
        return Result.ok("管理端面板");
    }

    @GetMapping("/demo/client/info")
    @RequireLogin(LoginType.APP)
    public Result<String> clientInfo() {
        return Result.ok("客户端信息");
    }

    @GetMapping("/demo/public/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }
}
