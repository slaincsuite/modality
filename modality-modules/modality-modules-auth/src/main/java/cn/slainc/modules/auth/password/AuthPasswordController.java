package cn.slainc.modules.auth.password;

import cn.slainc.core.result.Result;
import cn.slainc.modules.auth.login.CurrentLogin;
import cn.slainc.security.StpKit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/password")
public class AuthPasswordController {

    private final AuthPasswordService passwordService;

    public AuthPasswordController(AuthPasswordService passwordService) {
        this.passwordService = passwordService;
    }

    public record ChangeBody(String oldPassword, String newPassword) {
    }

    @PostMapping("/change")
    public Result<Void> change(@RequestBody ChangeBody body, HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var type = CurrentLogin.type(request);
        Long userId = loginUserId(type);
        passwordService.change(type, userId, body.oldPassword(), body.newPassword());
        return Result.ok();
    }

    @GetMapping("/status")
    public Result<AuthPasswordService.PasswordStatusView> status(HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var type = CurrentLogin.type(request);
        return Result.ok(passwordService.status(type, loginUserId(type)));
    }

    private Long loginUserId(cn.slainc.core.enums.LoginType type) {
        return CurrentLogin.userId(StpKit.of(type).getLoginId());
    }
}
