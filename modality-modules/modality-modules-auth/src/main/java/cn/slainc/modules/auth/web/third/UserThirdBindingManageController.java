package cn.slainc.modules.auth.web.third;

import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.dto.ThirdBindingQrRequest;
import cn.slainc.modules.auth.service.third.ThirdPartyLoginService;
import cn.slainc.modules.auth.service.user.TargetUserGuard;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.binding.ThirdAccountBinding;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "用户第三方绑定管理", description = "管理端查询、解绑与发起指定用户的第三方绑定")
@RestController
@RequestMapping("/upms/user/{userId}/third-binding")
public class UserThirdBindingManageController {

    private final ThirdPartyLoginService loginService;

    private final TargetUserGuard targetUserGuard;

    public UserThirdBindingManageController(ThirdPartyLoginService loginService, TargetUserGuard targetUserGuard) {
        this.loginService = loginService;
        this.targetUserGuard = targetUserGuard;
    }

    @GetMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_USER_QUERY)
    public Result<List<ThirdAccountBinding>> list(@PathVariable Long userId) {
        targetUserGuard.require(userId);
        return Result.ok(loginService.bindings(userId));
    }

    @DeleteMapping("/{platform}/{openId}")
    @RequirePermission(permissions = AuthKeys.PERMISSION_USER_UPDATE)
    @AuditLog(value = "管理员解绑用户第三方账号", operation = AuditOperation.REVOKE, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<Void> unbind(@PathVariable Long userId, @PathVariable String platform,
                               @PathVariable String openId) {
        targetUserGuard.require(userId);
        loginService.unbind(platform, openId, userId);
        return Result.ok();
    }

    @PostMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_USER_UPDATE)
    @AuditLog(value = "管理员发起用户第三方绑定", operation = AuditOperation.CREATE, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<ThirdPartyLoginService.QrCreateView> bindingQr(
            @PathVariable Long userId, @RequestBody(required = false) ThirdBindingQrRequest request) {
        targetUserGuard.require(userId);
        String platform = request == null || request.platform() == null || request.platform().isBlank()
                ? ThirdPartyPlatform.FEISHU.code() : request.platform();
        return Result.ok(loginService.createQrBinding(platform, userId, null));
    }
}
