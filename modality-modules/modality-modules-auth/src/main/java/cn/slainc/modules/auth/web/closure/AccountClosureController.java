package cn.slainc.modules.auth.web.closure;
import cn.slainc.modules.auth.closure.AccountClosureRecord;
import cn.slainc.modules.auth.service.closure.AccountClosureService;

import cn.slainc.modules.auth.service.token.AuthTokenService;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.Result;
import cn.slainc.modules.auth.service.login.AuthLogoutService;
import cn.slainc.modules.auth.context.CurrentLogin;
import cn.slainc.security.StpKit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "账号注销", description = "账号注销申请与处理")
@RestController
@RequestMapping("/auth/account")
public class AccountClosureController {

    private final AccountClosureService closureService;

    private final AuthLogoutService logoutService;

    public AccountClosureController(AccountClosureService closureService, AuthLogoutService logoutService) {
        this.closureService = closureService;
        this.logoutService = logoutService;
    }

    public record CloseBody(String password, String reason) {
    }

    @PostMapping("/close")
    public Result<AccountClosureRecord> close(@RequestBody CloseBody body, HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var type = CurrentLogin.type(request);
        Long userId = CurrentLogin.userId(StpKit.of(type).getLoginId());
        if (userId == null) {
            throw new BizException("当前登录身份缺少用户 ID，无法申请注销");
        }
        logoutService.checkPassword(type, userId, body.password());
        String username = logoutService.username(type, userId);
        return Result.ok(closureService.apply(type, userId, username, body.reason()));
    }

    @PostMapping("/close/cancel")
    public Result<Void> cancel(HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        closureService.cancel(loginUserId(CurrentLogin.type(request)));
        return Result.ok();
    }

    @GetMapping("/close/status")
    public Result<Optional<AccountClosureRecord>> status(HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        return Result.ok(closureService.latestStatus(loginUserId(CurrentLogin.type(request))));
    }

    private Long loginUserId(cn.slainc.core.enums.LoginType type) {
        return CurrentLogin.userId(StpKit.of(type).getLoginId());
    }
}
