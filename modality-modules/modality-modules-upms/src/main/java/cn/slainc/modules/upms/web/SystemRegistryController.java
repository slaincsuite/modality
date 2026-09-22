package cn.slainc.modules.upms.web;

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

@RestController
@RequestMapping("/upms/system")
public class SystemRegistryController {

    private final SystemRegistryService systemRegistryService;

    public SystemRegistryController(SystemRegistryService systemRegistryService) {
        this.systemRegistryService = systemRegistryService;
    }

    @PostMapping
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = "upms:system:create")
    public SystemRegistryRecord create(@RequestBody SystemRequest request) {
        return systemRegistryService.create(request.code(), request.name(), request.moduleCode(),
                request.visibility(), request.groupName(), request.icon(), request.url(), request.sort());
    }

    @PutMapping("/{id}")
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = "upms:system:update")
    public SystemRegistryRecord update(@PathVariable Long id, @RequestBody SystemRequest request) {
        return systemRegistryService.update(id, request.name(), request.groupName(), request.icon(), request.url(),
                request.sort(), request.status());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = "upms:system:delete")
    public void delete(@PathVariable Long id) {
        systemRegistryService.delete(id);
    }

    @PutMapping("/{id}/visibility")
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = "upms:system:visibility")
    public SystemRegistryRecord changeVisibility(@PathVariable Long id, @RequestBody VisibilityRequest request) {
        return systemRegistryService.changeVisibility(id, request.visibility());
    }

    @PutMapping("/{id}/status")
    @RequirePermission(value = LoginType.PLATFORM_ADMIN, permissions = "upms:system:update")
    public SystemRegistryRecord changeStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return systemRegistryService.update(id, null, null, null, null, null, request.status());
    }

    @GetMapping("/list")
    @RequirePermission(permissions = "upms:system:query")
    public List<SystemRegistryRecord> list(@RequestParam(required = false) String groupName,
                                           @RequestParam(required = false) String status) {
        return systemRegistryService.list(groupName, status);
    }
}
