package cn.slainc.modules.policy.web.manage;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.dto.PolicyBatchGrantRequest;
import cn.slainc.modules.policy.dto.PolicyBoundaryRequest;
import cn.slainc.modules.policy.dto.PolicyCopyRequest;
import cn.slainc.modules.policy.dto.PolicyCreateRequest;
import cn.slainc.modules.policy.dto.PolicyCustomTemplateRequest;
import cn.slainc.modules.policy.dto.PolicyGroupRequest;
import cn.slainc.modules.policy.dto.PolicyParseRequest;
import cn.slainc.modules.policy.dto.PolicyStatusRequest;
import cn.slainc.modules.policy.dto.PolicyUpdateRequest;
import cn.slainc.modules.policy.dto.TemplateImportRequest;
import cn.slainc.modules.policy.service.binding.PolicyBindingPortalService;
import cn.slainc.modules.policy.service.manage.PolicyManageService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.security.annotation.CheckMode;
import cn.slainc.web.http.DownloadResponses;
import jakarta.servlet.http.HttpServletResponse;
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

    private final PolicyBindingPortalService bindingService;

    public PolicyManageController(PolicyManageService manageService, PolicyBindingPortalService bindingService) {
        this.manageService = manageService;
        this.bindingService = bindingService;
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
                       @RequestParam(required = false) Boolean system,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return manageService.page(keyword, subjectType, subjectId, status, group, creator, system, current, size);
    }

    @GetMapping("/templates")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_QUERY)
    public Object templates() {
        return manageService.templates();
    }

    @GetMapping("/templates/custom")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_QUERY,
            PolicyCenterKeys.PERMISSION_POLICY_QUERY}, mode = CheckMode.ANY)
    public Object customTemplates() {
        return manageService.customTemplates();
    }

    @PostMapping("/templates/custom")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE,
            PolicyCenterKeys.PERMISSION_POLICY_CREATE}, mode = CheckMode.ANY)
    @AuditLog(value = "创建自定义策略模板", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public Object createCustomTemplate(@RequestBody PolicyCustomTemplateRequest request) {
        return manageService.createCustomTemplate(request, operator());
    }

    @PutMapping("/templates/custom/{id}")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE,
            PolicyCenterKeys.PERMISSION_POLICY_UPDATE}, mode = CheckMode.ANY)
    @AuditLog(value = "修改自定义策略模板", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public Object updateCustomTemplate(@PathVariable String id,
                                       @RequestBody PolicyCustomTemplateRequest request) {
        return manageService.updateCustomTemplate(id, request);
    }

    @PutMapping("/templates/custom")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE,
            PolicyCenterKeys.PERMISSION_POLICY_UPDATE}, mode = CheckMode.ANY)
    @AuditLog(value = "修改自定义策略模板", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public Object updateCustomTemplateByBody(@RequestBody PolicyCustomTemplateRequest request) {
        if (request.id() == null || request.id().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "修改自定义模板必须提供模板标识");
        }
        return manageService.updateCustomTemplate(request.id().trim(), request);
    }

    @DeleteMapping("/templates/custom/{id}")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE,
            PolicyCenterKeys.PERMISSION_POLICY_DELETE}, mode = CheckMode.ANY)
    @AuditLog(value = "删除自定义策略模板", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "policy")
    public void deleteCustomTemplate(@PathVariable String id) {
        manageService.deleteCustomTemplate(id);
    }

    @DeleteMapping("/templates/custom")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE,
            PolicyCenterKeys.PERMISSION_POLICY_DELETE}, mode = CheckMode.ANY)
    @AuditLog(value = "删除自定义策略模板", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "policy")
    public void deleteCustomTemplateByQuery(@RequestParam String id) {
        manageService.deleteCustomTemplate(id);
    }

    @PostMapping("/parse")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_VALIDATE,
            PolicyCenterKeys.PERMISSION_POLICY_QUERY}, mode = CheckMode.ANY)
    public Object parse(@RequestBody PolicyParseRequest request) {
        return manageService.parse(request.documentJson());
    }

    @PostMapping("/batch-grant")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_GRANT,
            PolicyCenterKeys.PERMISSION_BINDING_BIND}, mode = CheckMode.ANY)
    @AuditLog(value = "批量授权策略", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "policy")
    public Object batchGrant(@RequestBody PolicyBatchGrantRequest request) {
        return bindingService.batchGrant(request, operator());
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
    @AuditLog(value = "调整策略分组", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "policy")
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

    @GetMapping("/{id}/bindings")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_BINDING_QUERY)
    public Object bindings(@PathVariable String id) {
        return bindingService.sourceBindings(id);
    }

    @GetMapping("/{id}/bindings/export")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_BINDING_EXPORT,
            PolicyCenterKeys.PERMISSION_POLICY_TRANSFER}, mode = CheckMode.ANY)
    public void exportBindings(@PathVariable String id, HttpServletResponse response) {
        bindingService.exportBindings(id, DownloadResponses.attachmentStream(response,
                "policy-bindings-" + id + ".xlsx", DownloadResponses.CONTENT_TYPE_EXCEL));
    }

    @GetMapping("/{id}/boundary")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_BOUNDARY_QUERY,
            PolicyCenterKeys.PERMISSION_POLICY_QUERY}, mode = CheckMode.ANY)
    public Object boundary(@PathVariable String id) {
        return manageService.boundary(id);
    }

    @PostMapping("/{id}/boundary")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_BOUNDARY_UPDATE,
            PolicyCenterKeys.PERMISSION_POLICY_UPDATE}, mode = CheckMode.ANY)
    @AuditLog(value = "设置策略权限边界", operation = AuditOperation.CONFIG, category = AuditCategory.SECURITY,
            module = "policy", recordParams = false)
    public Object updateBoundary(@PathVariable String id, @RequestBody PolicyBoundaryRequest request) {
        return manageService.updateBoundary(id, request.documentJson(), operator());
    }

    private String operator() {
        return UserContext.username();
    }
}
