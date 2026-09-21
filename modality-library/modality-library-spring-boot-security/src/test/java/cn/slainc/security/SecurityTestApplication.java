package cn.slainc.security;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.Result;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.security.annotation.RequireRole;
import cn.slainc.security.annotation.RequireSafe;
import cn.slainc.security.ban.AccountBanService;
import cn.slainc.security.cache.SecurityPermissionCache;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.lock.AccountLockService;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.security.metrics.SecurityCounters;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.restrict.LoginRestrictionService;
import cn.slainc.security.safe.SafeAuthService;
import cn.slainc.security.stp.SecurityStpLogic;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@SpringBootConfiguration
@EnableAutoConfiguration
public class SecurityTestApplication {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public SecurityCounters testSecurityCounters() {
        return new SecurityCounters();
    }

    @Bean
    public TestAccountStore testAccountStore() {
        return new TestAccountStore();
    }

    @Bean
    public TestSaTokenListener testSaTokenListener() {
        return new TestSaTokenListener();
    }

    @Bean
    public TestEventCollector testEventCollector() {
        return new TestEventCollector();
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>
    tenantContextTestFilter() {
        return new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new jakarta.servlet.Filter() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response,
                                 jakarta.servlet.FilterChain chain) throws java.io.IOException,
                    jakarta.servlet.ServletException {
                jakarta.servlet.http.HttpServletRequest httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
                String tenantId = httpRequest.getHeader("X-Tenant-Id");
                try {
                    if (tenantId != null && !tenantId.isBlank()) {
                        cn.slainc.core.context.TenantContext.set(Long.parseLong(tenantId));
                    }
                    chain.doFilter(request, response);
                } finally {
                    cn.slainc.core.context.TenantContext.clear();
                }
            }
        });
    }

    @Bean
    public CountingPermissionProvider countingPermissionProvider() {
        return new CountingPermissionProvider();
    }

    @Bean
    public TestPolicyProvider testPolicyProvider() {
        return new TestPolicyProvider();
    }


    @RestController
    public static class AuthController {

        private final SecurityLoginService loginService;

        AuthController(SecurityLoginService loginService) {
            this.loginService = loginService;
        }

        public record LoginBody(String username, String password, String deviceType, String deviceId,
                                String clientIp) {
        }

        @PostMapping("/auth/login")
        public Result<LoginResult> login(@RequestHeader(value = "X-Login-Type", required = false) String loginType,
                                         @RequestBody LoginBody body) {
            return Result.ok(loginService.login(LoginType.of(loginType) == null ? LoginType.TENANT_USER
                            : LoginType.of(loginType),
                    new LoginRequest(body.username(), body.password(), body.deviceType(), body.deviceId(),
                            body.clientIp())));
        }

        @PostMapping("/auth/logout")
        public Result<Void> logout(@RequestHeader(value = "X-Login-Type", required = false) String loginType) {
            loginService.logout(LoginType.of(loginType) == null ? LoginType.TENANT_USER : LoginType.of(loginType));
            return Result.ok();
        }
    }

    @RestController
    public static class ProtectedController {

        private final SecurityLoginService loginService;

        ProtectedController(SecurityLoginService loginService) {
            this.loginService = loginService;
        }

        @GetMapping("/test/user/me")
        @RequireLogin
        public Result<Object> me() {
            return Result.ok(loginService.getLoginId(LoginType.TENANT_USER));
        }

        @GetMapping("/test/admin/panel")
        @RequireLogin(LoginType.PLATFORM_ADMIN)
        public Result<String> adminPanel() {
            return Result.ok("管理端面板");
        }

        @GetMapping("/test/client/info")
        @RequireLogin(LoginType.APP)
        public Result<String> clientInfo() {
            return Result.ok("客户端信息");
        }

        @GetMapping("/test/public/ping")
        public Result<String> ping() {
            return Result.ok("pong");
        }
    }

    @RestController
    public static class AdminOpsController {

        private final KickService kickService;

        private final SecurityLoginService loginService;

        private final AccountBanService banService;

        AdminOpsController(KickService kickService, SecurityLoginService loginService,
                           AccountBanService banService) {
            this.kickService = kickService;
            this.loginService = loginService;
            this.banService = banService;
        }

        @PostMapping("/test/admin/kick")
        @RequireLogin(LoginType.PLATFORM_ADMIN)
        public Result<Void> kick(@RequestParam String type, @RequestParam Long loginId,
                                 @RequestParam(required = false) String token,
                                 @RequestParam(required = false) String deviceType,
                                 @RequestParam(defaultValue = "管理员强制下线") String reason) {
            LoginType loginType = LoginType.of(type);
            if (token != null && !token.isBlank()) {
                kickService.kickoutToken(loginType, token, reason);
            } else if (deviceType != null && !deviceType.isBlank()) {
                kickService.kickout(loginType, loginId, deviceType, reason);
            } else {
                kickService.kickout(loginType, loginId, reason);
            }
            return Result.ok();
        }

        @GetMapping("/test/admin/online")
        @RequireLogin(LoginType.PLATFORM_ADMIN)
        public Result<List<Map<String, Object>>> online(@RequestParam String type,
                                                        @RequestParam Long loginId) {
            return Result.ok(loginService.onlineTerminals(LoginType.of(type), loginId));
        }

        @PostMapping("/test/admin/ban")
        @RequireLogin(LoginType.PLATFORM_ADMIN)
        public Result<Void> ban(@RequestParam String type, @RequestParam Long loginId,
                                @RequestParam(defaultValue = "60") long seconds) {
            banService.ban(LoginType.of(type), loginId, seconds);
            return Result.ok();
        }

        @PostMapping("/test/admin/unban")
        @RequireLogin(LoginType.PLATFORM_ADMIN)
        public Result<Void> unban(@RequestParam String type, @RequestParam Long loginId) {
            banService.unban(LoginType.of(type), loginId);
            return Result.ok();
        }
    }

    @RestController
    public static class PermissionController {

        private final SecurityPermissionCache cache;

        private final StpInterface stpInterface;

        PermissionController(SecurityPermissionCache cache, cn.dev33.satoken.stp.StpInterface stpInterface) {
            this.cache = cache;
            this.stpInterface = stpInterface;
        }

        @GetMapping("/test/permissions")
        public Result<List<String>> permissions(@RequestParam String type, @RequestParam Long loginId) {
            return Result.ok(stpInterface.getPermissionList(loginId, type));
        }

        @GetMapping("/test/roles")
        public Result<List<String>> roles(@RequestParam String type, @RequestParam Long loginId) {
            return Result.ok(stpInterface.getRoleList(loginId, type));
        }

        @PostMapping("/test/cache/invalidate")
        public Result<Void> invalidate(@RequestParam Long loginId,
                                       @RequestParam(required = false) String type) {
            if (type == null) {
                cache.invalidate(loginId);
            } else {
                cache.invalidate(loginId, LoginType.of(type));
            }
            return Result.ok();
        }
    }

    @RestController
    public static class SafeController {

        private final SafeAuthService safeAuthService;

        SafeController(SafeAuthService safeAuthService) {
            this.safeAuthService = safeAuthService;
        }

        @RequireLogin
        @PostMapping("/test/safe/open")
        public Result<Void> open(@RequestParam(defaultValue = "TENANT_USER") String type,
                                 @RequestParam(defaultValue = "pay") String service,
                                 @RequestParam(defaultValue = "60") long seconds) {
            safeAuthService.openSafe(LoginType.of(type), service, seconds);
            return Result.ok();
        }

        @RequireLogin
        @GetMapping("/test/safe/state")
        public Result<Boolean> state(@RequestParam(defaultValue = "TENANT_USER") String type,
                                     @RequestParam(defaultValue = "pay") String service) {
            return Result.ok(safeAuthService.isSafe(LoginType.of(type), service));
        }

        @RequireLogin
        @PostMapping("/test/safe/close")
        public Result<Void> close(@RequestParam(defaultValue = "TENANT_USER") String type,
                                  @RequestParam(defaultValue = "pay") String service) {
            safeAuthService.closeSafe(LoginType.of(type), service);
            return Result.ok();
        }

        @GetMapping("/test/safe/annotated")
        @RequireLogin
        @RequireSafe
        public Result<String> annotatedSafe() {
            return Result.ok("二次认证通过");
        }
    }

    @RestController
    public static class PasswordController {

        private final PasswordPolicyService passwordPolicy;

        PasswordController(PasswordPolicyService passwordPolicy) {
            this.passwordPolicy = passwordPolicy;
        }

        public record ChangeBody(String type, Long userId, String oldPassword, String newPassword,
                                 boolean kickOtherSessions) {
        }

        @PostMapping("/test/password/change")
        public Result<Void> change(@RequestBody ChangeBody body) {
            passwordPolicy.changePassword(LoginType.of(body.type()), body.userId(), body.oldPassword(),
                    body.newPassword(), body.kickOtherSessions());
            return Result.ok();
        }
    }

    @RestController
    public static class AnnotatedController {

        @GetMapping("/test/annotated/role-all")
        @RequireLogin
        @RequireRole(roles = {"user", "operator"})
        public Result<String> roleAll() {
            return Result.ok("同时拥有 user 与 operator 角色");
        }

        @GetMapping("/test/annotated/role-any")
        @RequireLogin
        @RequireRole(roles = {"super", "operator"}, mode = cn.slainc.security.annotation.CheckMode.ANY)
        public Result<String> roleAny() {
            return Result.ok("拥有任一角色");
        }

        @GetMapping("/test/annotated/perm-all")
        @RequireLogin
        @RequirePermission(permissions = {"order:read", "order:write"})
        public Result<String> permAll() {
            return Result.ok("同时拥有权限");
        }

        @GetMapping("/test/annotated/perm-any")
        @RequireLogin
        @RequirePermission(permissions = {"order:export", "order:none"}, mode = cn.slainc.security.annotation.CheckMode.ANY)
        public Result<String> permAny() {
            return Result.ok("拥有任一权限");
        }

        @GetMapping("/test/annotated/safe-only")
        @RequireSafe
        public Result<String> safeOnly() {
            return Result.ok("safe-only");
        }

        @GetMapping("/test/annotated/sa-check-login")
        @cn.dev33.satoken.annotation.SaCheckLogin(type = "PLATFORM_ADMIN")
        public Result<String> saCheckLogin() {
            return Result.ok("内置注解通过");
        }

        @GetMapping("/blacklisted/panel")
        public Result<String> blacklisted() {
            return Result.ok("黑名单内容");
        }

        @GetMapping("/test/restrict/echo")
        public Result<String> restrict(@RequestParam(defaultValue = "TENANT_USER") String type,
                                       @RequestParam(required = false) String clientIp,
                                       @RequestParam(required = false) String deviceType,
                                       LoginRestrictionService restrictionService) {
            restrictionService.check(LoginType.of(type), clientIp, deviceType);
            return Result.ok("允许登录");
        }
    }

    @RestController
    public static class LockProbeController {

        private final AccountLockService lockService;

        LockProbeController(AccountLockService lockService) {
            this.lockService = lockService;
        }

        @PostMapping("/test/lock/fail")
        public Result<Integer> recordFailure(@RequestParam String type, @RequestParam Long loginId) {
            return Result.ok(lockService.recordFailure(LoginType.of(type), loginId));
        }

        @GetMapping("/test/lock/state")
        public Result<Boolean> locked(@RequestParam String type, @RequestParam Long loginId) {
            return Result.ok(lockService.isLocked(LoginType.of(type), loginId));
        }

        @PostMapping("/test/lock/reset")
        public Result<Void> reset(@RequestParam String type, @RequestParam Long loginId) {
            lockService.reset(LoginType.of(type), loginId);
            return Result.ok();
        }
    }

    @RestControllerAdvice
    public static class TestBizExceptionAdvice {

        @ExceptionHandler(BizException.class)
        public Result<Void> handleBiz(BizException e) {
            return Result.fail(400100, e.getMessage());
        }
    }
}
