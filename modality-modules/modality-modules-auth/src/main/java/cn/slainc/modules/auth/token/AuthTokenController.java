package cn.slainc.modules.auth.token;

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
@RequestMapping("/auth/token")
public class AuthTokenController {

    private final AuthTokenService tokenService;

    public AuthTokenController(AuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public record TokenBody(String token) {
    }

    @PostMapping("/refresh")
    public Result<AuthTokenService.TokenPairView> refresh(@RequestBody TokenBody body) {
        return Result.ok(tokenService.refresh(body.token()));
    }

    @PostMapping("/revoke")
    public Result<Boolean> revoke(@RequestBody TokenBody body) {
        return Result.ok(tokenService.revoke(body.token()));
    }

    @PostMapping("/introspect")
    public Result<Object> introspect(@RequestBody TokenBody body) {
        return Result.ok(tokenService.introspect(body.token()));
    }

    @GetMapping("/info")
    public Result<Object> info(HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        return Result.ok(StpKit.of(CurrentLogin.type(request)).getTokenInfo());
    }
}
