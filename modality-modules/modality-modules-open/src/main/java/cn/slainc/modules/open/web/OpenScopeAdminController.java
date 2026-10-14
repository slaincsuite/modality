package cn.slainc.modules.open.web;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.dto.ScopeRequest;
import cn.slainc.openapi.domain.OpenScope;
import cn.slainc.openapi.domain.ScopeRiskLevel;
import cn.slainc.openapi.spi.ScopeService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "授权范围管理", description = "scope 定义维护")
@RestController
@RequestMapping("/open/admin/scopes")
public class OpenScopeAdminController {

    private final ScopeService scopeService;

    public OpenScopeAdminController(ScopeService scopeService) {
        this.scopeService = scopeService;
    }

    @GetMapping
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_QUERY)
    public Object list(@RequestParam(required = false) String group) {
        return group == null || group.isBlank() ? scopeService.list() : scopeService.listByGroup(group);
    }

    @GetMapping("/groups")
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_QUERY)
    public Object groups() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OpenScope scope : scopeService.list()) {
            counts.merge(scope.getGroup(), 1L, Long::sum);
        }
        return counts;
    }

    @PostMapping
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_CREATE)
    @AuditLog(value = "登记开放平台 Scope", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "open")
    public Object create(@RequestBody ScopeRequest request) {
        OpenScope scope = build(request);
        scopeService.register(scope);
        return scope;
    }

    @PutMapping("/{name}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_UPDATE)
    @AuditLog(value = "修改开放平台 Scope", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "open")
    public Object update(@PathVariable String name, @RequestBody ScopeRequest request) {
        scopeService.get(name);
        OpenScope scope = build(request);
        scope.setName(name);
        scopeService.update(scope);
        return scope;
    }

    @DeleteMapping("/{name}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_DELETE)
    @AuditLog(value = "删除开放平台 Scope", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "open")
    public void delete(@PathVariable String name) {
        scopeService.remove(name);
    }

    private OpenScope build(ScopeRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "Scope 中文名称不能为空");
        }
        boolean requiresAudit = request.requiresAudit() != null && request.requiresAudit();
        OpenScope scope = new OpenScope();
        scope.setName(request.name());
        scope.setTitle(request.title().trim());
        scope.setDescription(request.description());
        scope.setGroup(request.group() == null || request.group().isBlank()
                ? OpenKeys.DEFAULT_SCOPE_GROUP : request.group().trim());
        scope.setRequiresAudit(requiresAudit);
        if (request.riskLevel() != null && !request.riskLevel().isBlank()) {
            try {
                scope.setRiskLevel(ScopeRiskLevel.valueOf(request.riskLevel().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        "风险等级只允许 LOW、MIDDLE、HIGH，当前为 " + request.riskLevel());
            }
        }
        if (scope.getRiskLevel() != ScopeRiskLevel.LOW) {
            scope.setRequiresAudit(true);
        }
        return scope;
    }
}
