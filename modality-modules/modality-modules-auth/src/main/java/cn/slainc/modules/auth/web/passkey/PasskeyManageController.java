package cn.slainc.modules.auth.web.passkey;

import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.dto.PasskeyCreateRequest;
import cn.slainc.modules.auth.service.passkey.PasskeyService;
import cn.slainc.modules.auth.service.user.TargetUserGuard;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "用户通行密钥", description = "管理端维护通行密钥注册元数据")
@RestController
@RequestMapping("/upms/user/{userId}/passkeys")
public class PasskeyManageController {

    private final PasskeyService passkeyService;

    private final TargetUserGuard targetUserGuard;

    public PasskeyManageController(PasskeyService passkeyService, TargetUserGuard targetUserGuard) {
        this.passkeyService = passkeyService;
        this.targetUserGuard = targetUserGuard;
    }

    @GetMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_USER_QUERY)
    public Result<List<PasskeyService.PasskeyView>> list(@PathVariable Long userId) {
        targetUserGuard.require(userId);
        return Result.ok(passkeyService.list(userId));
    }

    @PostMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_USER_UPDATE)
    @AuditLog(value = "创建用户通行密钥元数据", operation = AuditOperation.CREATE, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<PasskeyService.PasskeyView> create(@PathVariable Long userId,
                                                     @RequestBody PasskeyCreateRequest request) {
        targetUserGuard.require(userId);
        return Result.ok(passkeyService.create(userId, request));
    }

    @DeleteMapping("/{credentialId}")
    @RequirePermission(permissions = AuthKeys.PERMISSION_USER_UPDATE)
    @AuditLog(value = "删除用户通行密钥元数据", operation = AuditOperation.DELETE, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<Void> delete(@PathVariable Long userId, @PathVariable String credentialId) {
        targetUserGuard.require(userId);
        passkeyService.delete(userId, credentialId);
        return Result.ok();
    }

    @DeleteMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_USER_UPDATE)
    @AuditLog(value = "删除用户通行密钥元数据", operation = AuditOperation.DELETE, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<Void> deleteByQuery(@PathVariable Long userId, @RequestParam String credentialId) {
        targetUserGuard.require(userId);
        passkeyService.delete(userId, credentialId);
        return Result.ok();
    }
}
