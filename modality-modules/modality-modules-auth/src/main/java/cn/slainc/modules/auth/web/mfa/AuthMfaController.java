package cn.slainc.modules.auth.web.mfa;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.Result;
import cn.slainc.modules.auth.context.CurrentLogin;
import cn.slainc.modules.auth.service.login.AuthLoginService;
import cn.slainc.modules.auth.service.mfa.AuthMfaService;
import cn.slainc.security.StpKit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "二次验证", description = "TOTP 动态口令绑定、登录二次校验与恢复码管理")
@RestController
@RequestMapping("/auth/mfa")
public class AuthMfaController {

    private final AuthMfaService mfaService;

    public AuthMfaController(AuthMfaService mfaService) {
        this.mfaService = mfaService;
    }

    public record CodeBody(String code) {
    }

    public record VerifyBody(String challengeId, String code) {
    }

    @GetMapping("/status")
    public Result<AuthMfaService.MfaStatusView> status(HttpServletRequest request) {
        var type = currentType(request);
        return Result.ok(mfaService.status(type, loginUserId(type)));
    }

    @PostMapping("/enroll")
    public Result<AuthMfaService.MfaEnrollView> enroll(HttpServletRequest request) {
        var type = currentType(request);
        return Result.ok(mfaService.enroll(type, loginUserId(type)));
    }

    @PostMapping("/activate")
    public Result<Void> activate(@RequestBody CodeBody body, HttpServletRequest request) {
        requireCode(body);
        var type = currentType(request);
        mfaService.activate(type, loginUserId(type), body.code());
        return Result.ok();
    }

    @PostMapping("/disable")
    public Result<Void> disable(@RequestBody CodeBody body, HttpServletRequest request) {
        requireCode(body);
        var type = currentType(request);
        mfaService.disable(type, loginUserId(type), body.code());
        return Result.ok();
    }

    @PostMapping("/recovery-codes")
    public Result<List<String>> recoveryCodes(@RequestBody CodeBody body, HttpServletRequest request) {
        requireCode(body);
        var type = currentType(request);
        return Result.ok(mfaService.regenerateRecoveryCodes(type, loginUserId(type), body.code()));
    }

    // 登录第二步：挑战码换正式登录态，匿名可达，需在安全过滤白名单中放行 /auth/mfa/verify
    @PostMapping("/verify")
    public Result<AuthLoginService.AuthLoginView> verify(@RequestBody VerifyBody body) {
        return Result.ok(mfaService.verifySecondFactor(body.challengeId(), body.code()));
    }

    private cn.slainc.core.enums.LoginType currentType(HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        return CurrentLogin.type(request);
    }

    private Long loginUserId(cn.slainc.core.enums.LoginType type) {
        return CurrentLogin.userId(StpKit.of(type).getLoginId());
    }

    private void requireCode(CodeBody body) {
        if (body == null || body.code() == null || body.code().isBlank()) {
            throw new BizException("动态码不能为空");
        }
    }
}
