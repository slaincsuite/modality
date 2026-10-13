package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.dto.StatusRequest;
import cn.slainc.modules.upms.dto.SystemRequest;
import cn.slainc.modules.upms.dto.VisibilityRequest;
import cn.slainc.modules.upms.entity.SystemRegistryRecord;
import cn.slainc.modules.upms.service.SystemRegistryService;
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

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditOperation;

@Tag(name = "系统注册", description = "接入系统与模块注册维护")
@RestController
@RequestMapping("/upms/system")
public class SystemRegistryController {

    private final SystemRegistryService systemRegistryService;

    public SystemRegistryController(SystemRegistryService systemRegistryService) {
        this.systemRegistryService = systemRegistryService;
    }

    @PostMapping
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = UpmsKeys.PERMISSION_UPMS_SYSTEM_CREATE)
    @AuditLog(value = "注册系统", operation = AuditOperation.CREATE, category = AuditCategory.SYSTEM,
            module = "upms")
    public SystemRegistryRecord create(@RequestBody SystemRequest request) {
        return systemRegistryService.create(request.code(), request.name(), request.moduleCode(),
                request.visibility(), request.groupName(), request.icon(), request.url(), request.sort());
    }

    @PutMapping("/{id}")
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = UpmsKeys.PERMISSION_UPMS_SYSTEM_UPDATE)
    @AuditLog(value = "修改系统", operation = AuditOperation.UPDATE, category = AuditCategory.SYSTEM,
            module = "upms")
    public SystemRegistryRecord update(@PathVariable Long id, @RequestBody SystemRequest request) {
        return systemRegistryService.update(id, request.name(), request.groupName(), request.icon(), request.url(),
                request.sort(), request.status());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = UpmsKeys.PERMISSION_UPMS_SYSTEM_DELETE)
    @AuditLog(value = "删除系统", operation = AuditOperation.DELETE, category = AuditCategory.SYSTEM,
            module = "upms")
    public void delete(@PathVariable Long id) {
        systemRegistryService.delete(id);
    }

    @PutMapping("/{id}/visibility")
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = UpmsKeys.PERMISSION_UPMS_SYSTEM_VISIBILITY)
    @AuditLog(value = "修改系统可见性", operation = AuditOperation.UPDATE, category = AuditCategory.SYSTEM,
            module = "upms")
    public SystemRegistryRecord changeVisibility(@PathVariable Long id, @RequestBody VisibilityRequest request) {
        return systemRegistryService.changeVisibility(id, request.visibility());
    }

    @PutMapping("/{id}/status")
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = UpmsKeys.PERMISSION_UPMS_SYSTEM_UPDATE)
    @AuditLog(value = "修改系统状态", operation = AuditOperation.UPDATE, category = AuditCategory.SYSTEM,
            module = "upms")
    public SystemRegistryRecord changeStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return systemRegistryService.update(id, null, null, null, null, null, request.status());
    }

    @GetMapping("/list")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_SYSTEM_QUERY)
    public List<SystemRegistryRecord> list(@RequestParam(required = false) String groupName,
                                           @RequestParam(required = false) String status) {
        return systemRegistryService.list(groupName, status);
    }
}
