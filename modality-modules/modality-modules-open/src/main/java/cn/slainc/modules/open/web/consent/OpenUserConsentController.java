package cn.slainc.modules.open.web.consent;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.open.service.consent.OpenConsentAdminService;
import cn.slainc.security.annotation.RequireLogin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

// 用户视角授权管理：本人授权记录与一键解约（本人凭据端点）
@Tag(name = "我的授权", description = "用户视角授权查看与撤销")
@RestController
@RequestMapping("/open/my/consents")
public class OpenUserConsentController {

    private final OpenConsentAdminService consentAdminService;

    public OpenUserConsentController(OpenConsentAdminService consentAdminService) {
        this.consentAdminService = consentAdminService;
    }

    @GetMapping
    @RequireLogin
    public Object myConsents(@RequestParam(required = false) String appId,
                             @RequestParam(required = false) Boolean active) {
        String userId = requireUserId();
        return consentAdminService.listForUser(userId, appId, active);
    }

    @PostMapping("/{appId}")
    @RequireLogin
    public Object grant(@PathVariable String appId, @RequestBody ScopeGrantRequest request) {
        return consentAdminService.grantByUser(requireUserId(), appId, request.scopes());
    }

    public record ScopeGrantRequest(List<String> scopes) {
    }

    @DeleteMapping("/{appId}")
    @RequireLogin
    public Object revoke(@PathVariable String appId) {
        return consentAdminService.revokeByUser(requireUserId(), appId);
    }

    @DeleteMapping("/{appId}/scopes/{scope}")
    @RequireLogin
    public Object revokeScope(@PathVariable String appId, @PathVariable String scope) {
        return consentAdminService.revokeScope(requireUserId(), appId, scope);
    }

    private String requireUserId() {
        Long userId = UserContext.userId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户身份未识别，请先登录");
        }
        return String.valueOf(userId);
    }
}
