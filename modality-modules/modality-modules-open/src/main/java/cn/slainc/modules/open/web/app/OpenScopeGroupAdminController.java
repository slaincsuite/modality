package cn.slainc.modules.open.web.app;

import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.dto.ScopeGroupRequest;
import cn.slainc.modules.open.service.app.ScopeGroupService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Scope 分组管理", description = "授权范围分组维护")
@RestController
@RequestMapping("/open/admin/scope-groups")
public class OpenScopeGroupAdminController {

    private final ScopeGroupService groupService;

    public OpenScopeGroupAdminController(ScopeGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_QUERY)
    public Object list() {
        return groupService.list();
    }

    @PostMapping
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_CREATE)
    @AuditLog(value = "创建 Scope 分组", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "open")
    public Object create(@RequestBody ScopeGroupRequest request) {
        return groupService.create(request);
    }

    @PutMapping("/{name}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_UPDATE)
    @AuditLog(value = "修改 Scope 分组", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "open")
    public Object update(@PathVariable String name, @RequestBody ScopeGroupRequest request) {
        return groupService.update(name, request);
    }

    @PutMapping
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_UPDATE)
    @AuditLog(value = "修改 Scope 分组", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "open")
    public Object updateByBody(@RequestBody ScopeGroupRequest request) {
        return groupService.update(request == null ? null : request.name(), request);
    }

    @DeleteMapping("/{name}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_DELETE)
    @AuditLog(value = "删除 Scope 分组", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "open")
    public void delete(@PathVariable String name) {
        groupService.delete(name);
    }

    @DeleteMapping
    @RequirePermission(permissions = OpenKeys.PERMISSION_SCOPE_DELETE)
    @AuditLog(value = "删除 Scope 分组", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "open")
    public void deleteByName(@RequestParam String name) {
        groupService.delete(name);
    }
}
