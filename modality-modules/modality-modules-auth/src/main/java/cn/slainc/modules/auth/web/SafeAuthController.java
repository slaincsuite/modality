package cn.slainc.modules.auth.web;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.Result;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.auth.service.AuthLogoutService;
import cn.slainc.modules.auth.context.CurrentLogin;
import cn.slainc.security.StpKit;
import cn.slainc.security.safe.SafeAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

// 二次认证：敏感操作前重新校验密码，通过后按服务名开放一段时间的安全窗口
@Tag(name = "安全验证", description = "敏感操作前的二次身份校验")
@RestController
@RequestMapping("/auth/safe")
public class SafeAuthController {

    private final SafeAuthService safeAuthService;

    private final AuthLogoutService logoutService;

    private final AuthProperties properties;

    public SafeAuthController(SafeAuthService safeAuthService, AuthLogoutService logoutService,
                              AuthProperties properties) {
        this.safeAuthService = safeAuthService;
        this.logoutService = logoutService;
        this.properties = properties;
    }

    public record OpenBody(String password, String service) {
    }

    @PostMapping("/open")
    public Result<Long> open(@RequestBody OpenBody body, HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var type = CurrentLogin.type(request);
        Long userId = CurrentLogin.userId(StpKit.of(type).getLoginId());
        if (userId == null) {
            throw new BizException("当前登录身份缺少用户 ID，无法开启二次认证");
        }
        logoutService.checkPassword(type, userId, body.password());
        String service = body.service() == null || body.service().isBlank() ? "DEFAULT" : body.service();
        safeAuthService.openSafe(type, service, properties.getSafe().getTtlSeconds());
        return Result.ok(properties.getSafe().getTtlSeconds());
    }

    @GetMapping("/status")
    public Result<Boolean> status(@RequestParam(defaultValue = "DEFAULT") String service,
                                  HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        return Result.ok(safeAuthService.isSafe(CurrentLogin.type(request), service));
    }

    @PostMapping("/close")
    public Result<Void> close(@RequestParam(defaultValue = "DEFAULT") String service,
                              HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        safeAuthService.closeSafe(CurrentLogin.type(request), service);
        return Result.ok();
    }
}
