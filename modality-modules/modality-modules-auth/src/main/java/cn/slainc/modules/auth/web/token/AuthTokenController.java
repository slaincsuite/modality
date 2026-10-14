package cn.slainc.modules.auth.web.token;

import cn.slainc.modules.auth.service.token.AuthTokenService;

import cn.slainc.core.result.Result;
import cn.slainc.modules.auth.context.CurrentLogin;
import cn.slainc.security.StpKit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "令牌管理", description = "访问令牌刷新")
@RestController
@RequestMapping("/auth/token")
public class AuthTokenController {

    private final AuthTokenService tokenService;

    public AuthTokenController(AuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public record TokenBody(String token) {
    }

    public record IntrospectBody(String token, String clientId, String clientSecret) {
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
    public Result<Object> introspect(@RequestBody IntrospectBody body) {
        return Result.ok(tokenService.introspect(body.token(), body.clientId(), body.clientSecret()));
    }

    @GetMapping("/info")
    public Result<Object> info(HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        return Result.ok(StpKit.of(CurrentLogin.type(request)).getTokenInfo());
    }
}
