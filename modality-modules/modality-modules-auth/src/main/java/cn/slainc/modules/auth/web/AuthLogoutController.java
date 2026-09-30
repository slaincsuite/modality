package cn.slainc.modules.auth.web;
import cn.slainc.modules.auth.service.AuthLogoutService;
import cn.slainc.modules.auth.context.CurrentLogin;

import cn.slainc.modules.auth.service.AuthLoginService;

import cn.slainc.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

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
