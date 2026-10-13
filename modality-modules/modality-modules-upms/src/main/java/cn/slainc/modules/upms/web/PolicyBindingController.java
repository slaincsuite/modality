package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.core.context.UserContext;
import cn.slainc.modules.upms.dto.PolicyBindClientRequest;
import cn.slainc.modules.upms.dto.PolicyBindRoleRequest;
import cn.slainc.modules.upms.dto.PolicyBindUserRequest;
import cn.slainc.policy.admin.PolicyBindingService;
import cn.slainc.policy.model.PolicyBinding;
import cn.slainc.security.annotation.RequirePermission;
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
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditOperation;

@Tag(name = "策略绑定", description = "用户与策略绑定关系的查询管理")
@RestController
@RequestMapping("/upms/policy-binding")
public class PolicyBindingController {

    private final PolicyBindingService bindingService;

    public PolicyBindingController(PolicyBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @PostMapping("/user")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_POLICY_BIND)
    @AuditLog(value = "绑定用户策略", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "upms")
    public PolicyBinding bindUser(@RequestBody PolicyBindUserRequest request) {
        return bindingService.bindToUser(request.policyId(), request.userId(), request.loginType(),
                UserContext.username());
    }

    @PostMapping("/role")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_POLICY_BIND)
    @AuditLog(value = "绑定角色策略", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "upms")
    public PolicyBinding bindRole(@RequestBody PolicyBindRoleRequest request) {
        return bindingService.bindToRole(request.policyId(), request.roleCode(), UserContext.username());
    }

    @PostMapping("/client")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_POLICY_BIND)
    @AuditLog(value = "绑定 Client 策略", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "upms")
    public PolicyBinding bindClient(@RequestBody PolicyBindClientRequest request) {
        return bindingService.bindToClient(request.policyId(), request.clientId(), UserContext.username());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_POLICY_UNBIND)
    @AuditLog(value = "解绑策略", operation = AuditOperation.REVOKE, category = AuditCategory.PERMISSION,
            module = "upms")
    public void unbind(@PathVariable Long id) {
        bindingService.unbind(id);
    }

    @GetMapping("/query")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_POLICY_QUERY)
    public List<PolicyBinding> query(@RequestParam(required = false) String subjectType,
                                           @RequestParam(required = false) String subjectId,
                                           @RequestParam(required = false) String policyId) {
        return bindingService.query(subjectType, subjectId, policyId);
    }
}
