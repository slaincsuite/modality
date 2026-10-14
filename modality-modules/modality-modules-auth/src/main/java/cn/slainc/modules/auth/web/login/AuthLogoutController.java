package cn.slainc.modules.auth.web.login;
import cn.slainc.modules.auth.service.login.AuthLogoutService;
import cn.slainc.modules.auth.context.CurrentLogin;

import cn.slainc.modules.auth.service.login.AuthLoginService;

import cn.slainc.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "注销", description = "退出登录与令牌作废")
@RestController
public class AuthLogoutController {

    private final AuthLogoutService logoutService;

    public AuthLogoutController(AuthLogoutService logoutService) {
        this.logoutService = logoutService;
    }

    @PostMapping("/auth/logout")
    public Result<Boolean> logout(HttpServletRequest request) {
        return Result.ok(logoutService.logoutCurrent(CurrentLogin.type(request)));
    }

    @PostMapping("/auth/logout/all")
    public Result<Integer> logoutAll(HttpServletRequest request) {
        return Result.ok(logoutService.logoutAll(CurrentLogin.type(request)));
    }
}
