package cn.slainc.modules.auth.web.mfa;

import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.service.mfa.AuthMfaService;
import cn.slainc.modules.auth.service.user.TargetUserGuard;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户二次验证管理", description = "管理端查询与解绑指定用户的二次验证")
@RestController
@RequestMapping("/upms/user/{userId}/mfa")
public class UserMfaManageController {

    private final AuthMfaService mfaService;

    private final TargetUserGuard targetUserGuard;

    public UserMfaManageController(AuthMfaService mfaService, TargetUserGuard targetUserGuard) {
        this.mfaService = mfaService;
        this.targetUserGuard = targetUserGuard;
    }

    @GetMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_USER_QUERY)
    public Result<AuthMfaService.AdminMfaStatusView> status(@PathVariable Long userId) {
        targetUserGuard.require(userId);
        return Result.ok(mfaService.adminStatus(userId));
    }

    @DeleteMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_USER_UPDATE)
    @AuditLog(value = "管理员解绑用户二次验证", operation = AuditOperation.DELETE, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<Void> disable(@PathVariable Long userId) {
        targetUserGuard.require(userId);
        mfaService.adminDisable(userId);
        return Result.ok();
    }
}
