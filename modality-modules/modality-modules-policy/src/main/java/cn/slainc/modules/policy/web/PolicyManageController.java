package cn.slainc.modules.policy.web;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.dto.PolicyCopyRequest;
import cn.slainc.modules.policy.dto.PolicyCreateRequest;
import cn.slainc.modules.policy.dto.PolicyGroupRequest;
import cn.slainc.modules.policy.dto.PolicyStatusRequest;
import cn.slainc.modules.policy.dto.PolicyUpdateRequest;
import cn.slainc.modules.policy.dto.TemplateImportRequest;
import cn.slainc.modules.policy.service.PolicyManageService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "策略管理", description = "策略创建、版本与状态管理")
@RestController
@RequestMapping("/policy/manage")
public class PolicyManageController {

    private final PolicyManageService manageService;

    public PolicyManageController(PolicyManageService manageService) {
        this.manageService = manageService;
    }

    @PostMapping
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_CREATE)
    @AuditLog(value = "创建策略", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public Object create(@RequestBody PolicyCreateRequest request) {
        return manageService.create(request, operator());
    }

    @GetMapping("/page")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_QUERY)
    public Object page(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String subjectType,
                       @RequestParam(required = false) String subjectId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String group,
                       @RequestParam(required = false) String creator,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return manageService.page(keyword, subjectType, subjectId, status, group, creator, current, size);
    }

    @GetMapping("/templates")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_QUERY)
    public Object templates() {
        return manageService.templates();
    }

    @PostMapping("/system/seed")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_CREATE)
    @AuditLog(value = "预置系统策略", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "policy")
    public Object seedSystemPolicies() {
        return manageService.seedSystemPolicies(operator());
    }

    @PostMapping("/from-template")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_CREATE)
    @AuditLog(value = "从模板创建策略", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "policy")
    public Object importFromTemplate(@RequestBody TemplateImportRequest request) {
        return manageService.importFromTemplate(request.template(), request.name(), request.subjectType(),
                request.subjectId(), request.group(), operator());
    }

    @GetMapping("/{id}")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_QUERY)
    public Object get(@PathVariable String id) {
        return manageService.view(id);
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_UPDATE)
    @AuditLog(value = "修改策略", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public Object update(@PathVariable String id, @RequestBody PolicyUpdateRequest request) {
        return manageService.updateDocument(id, request.documentJson(), operator());
    }

    @PutMapping("/{id}/status")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_UPDATE)
    @AuditLog(value = "切换策略状态", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "policy")
    public Object changeStatus(@PathVariable String id, @RequestBody PolicyStatusRequest request) {
        return manageService.changeStatus(id, request.enabled(), operator());
    }

    @PutMapping("/{id}/group")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_UPDATE)
    public Object changeGroup(@PathVariable String id, @RequestBody PolicyGroupRequest request) {
        return manageService.changeGroup(id, request.group());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_DELETE)
    @AuditLog(value = "删除策略", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "policy")
    public void delete(@PathVariable String id) {
        manageService.delete(id, operator());
    }

    @PostMapping("/{id}/copy")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_CREATE)
    @AuditLog(value = "复制策略", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "policy")
    public Object copy(@PathVariable String id, @RequestBody(required = false) PolicyCopyRequest request) {
        return manageService.copy(id, request == null ? null : request.name(), operator());
    }

    @GetMapping("/{id}/versions")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_VERSION)
    public Object versions(@PathVariable String id) {
        return manageService.versions(id);
    }

    @GetMapping("/{id}/diff")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_VERSION)
    public Object diff(@PathVariable String id, @RequestParam int fromVersion, @RequestParam int toVersion) {
        return manageService.diff(id, fromVersion, toVersion);
    }

    @PostMapping("/{id}/rollback/{version}")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_VERSION)
    @AuditLog(value = "回滚策略版本", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "policy")
    public Object rollback(@PathVariable String id, @PathVariable int version) {
        return manageService.rollback(id, version, operator());
    }

    @GetMapping("/{id}/audit")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_QUERY)
    public Object audit(@PathVariable String id, @RequestParam(defaultValue = "20") int limit) {
        return manageService.audit(id, limit);
    }

    private String operator() {
        return UserContext.username();
    }
}
