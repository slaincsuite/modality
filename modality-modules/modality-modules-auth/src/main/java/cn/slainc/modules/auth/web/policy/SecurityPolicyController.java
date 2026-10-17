package cn.slainc.modules.auth.web.policy;

import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.dto.LoginPolicyRequest;
import cn.slainc.modules.auth.dto.MfaPolicyRequest;
import cn.slainc.modules.auth.dto.SessionPolicyRequest;
import cn.slainc.modules.auth.service.policy.SecurityPolicyService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证安全策略", description = "二次验证、异地登录与会话保持策略")
@RestController
@RequestMapping("/auth")
public class SecurityPolicyController {

    private final SecurityPolicyService policyService;

    public SecurityPolicyController(SecurityPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/mfa/policy")
    @RequirePermission(permissions = AuthKeys.PERMISSION_MFA_POLICY_QUERY)
    public Result<SecurityPolicyService.MfaPolicyView> mfaPolicy() {
        return Result.ok(policyService.mfaPolicy());
    }

    @PutMapping("/mfa/policy")
    @RequirePermission(permissions = AuthKeys.PERMISSION_MFA_POLICY_UPDATE)
    @AuditLog(value = "修改二次验证策略", operation = AuditOperation.CONFIG, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<SecurityPolicyService.MfaPolicyView> updateMfaPolicy(@RequestBody MfaPolicyRequest request) {
        return Result.ok(policyService.updateMfaPolicy(request.loginProtect(), request.operationProtect()));
    }

    @GetMapping("/login-policy")
    @RequirePermission(permissions = AuthKeys.PERMISSION_LOGIN_POLICY_QUERY)
    public Result<SecurityPolicyService.LoginPolicyView> loginPolicy() {
        return Result.ok(policyService.loginPolicy());
    }

    @PutMapping("/login-policy")
    @RequirePermission(permissions = AuthKeys.PERMISSION_LOGIN_POLICY_UPDATE)
    @AuditLog(value = "修改异地登录策略", operation = AuditOperation.CONFIG, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<SecurityPolicyService.LoginPolicyView> updateLoginPolicy(@RequestBody LoginPolicyRequest request) {
        return Result.ok(policyService.updateLoginPolicy(request.remoteCheck()));
    }

    @GetMapping("/session-policy")
    @RequirePermission(permissions = AuthKeys.PERMISSION_SESSION_POLICY_QUERY)
    public Result<SecurityPolicyService.SessionPolicyView> sessionPolicy() {
        return Result.ok(policyService.sessionPolicy());
    }

    @PutMapping("/session-policy")
    @RequirePermission(permissions = AuthKeys.PERMISSION_SESSION_POLICY_UPDATE)
    @AuditLog(value = "修改会话保持策略", operation = AuditOperation.CONFIG, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<SecurityPolicyService.SessionPolicyView> updateSessionPolicy(
            @RequestBody SessionPolicyRequest request) {
        return Result.ok(policyService.updateSessionPolicy(request.idleTimeoutSeconds(),
                request.maxSessionSeconds()));
    }
}
