package cn.slainc.modules.policy.web;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.dto.BindingBatchRequest;
import cn.slainc.modules.policy.dto.BindingClientRequest;
import cn.slainc.modules.policy.dto.BindingRoleRequest;
import cn.slainc.modules.policy.dto.BindingUserRequest;
import cn.slainc.modules.policy.service.PolicyBindingPortalService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/policy/bindings")
public class PolicyBindingPortalController {

    private final PolicyBindingPortalService bindingService;

    public PolicyBindingPortalController(PolicyBindingPortalService bindingService) {
        this.bindingService = bindingService;
    }

    @PostMapping("/user")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_BINDING_BIND)
    @AuditLog(value = "绑定用户策略", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "policy")
    public Object bindUser(@RequestBody BindingUserRequest request) {
        return bindingService.bindToUser(request.policyId(), request.userId(), request.loginType(),
                UserContext.username());
    }

    @PostMapping("/role")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_BINDING_BIND)
    @AuditLog(value = "绑定角色策略", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "policy")
    public Object bindRole(@RequestBody BindingRoleRequest request) {
        return bindingService.bindToRole(request.policyId(), request.roleCode(), UserContext.username());
    }

    @PostMapping("/client")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_BINDING_BIND)
    @AuditLog(value = "绑定 Client 策略", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "policy")
    public Object bindClient(@RequestBody BindingClientRequest request) {
        return bindingService.bindToClient(request.policyId(), request.clientId(), UserContext.username());
    }

    @PostMapping("/batch")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_BINDING_BIND)
    @AuditLog(value = "批量绑定策略", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "policy")
    public Object batchBind(@RequestBody BindingBatchRequest request) {
        return bindingService.batchBind(request, UserContext.username());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_BINDING_UNBIND)
    @AuditLog(value = "解绑策略", operation = AuditOperation.DELETE, category = AuditCategory.PERMISSION,
            module = "policy")
    public void unbind(@PathVariable Long id) {
        bindingService.unbind(id);
    }

    @GetMapping("/query")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_BINDING_QUERY)
    public Object query(@RequestParam(required = false) String subjectType,
                        @RequestParam(required = false) String subjectId,
                        @RequestParam(required = false) String policyId) {
        return bindingService.query(subjectType, subjectId, policyId);
    }

    @GetMapping("/subject")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_BINDING_QUERY)
    public Map<String, Object> subjectView(@RequestParam String subjectType, @RequestParam String subjectId) {
        return bindingService.subjectView(subjectType, subjectId);
    }
}
