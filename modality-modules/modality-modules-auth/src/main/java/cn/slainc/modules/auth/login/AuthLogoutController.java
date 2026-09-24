package cn.slainc.modules.auth.login;

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
