package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.modules.upms.dto.DataScopeRequest;
import cn.slainc.modules.upms.dto.RoleCreateRequest;
import cn.slainc.modules.upms.dto.RolePermissionRequest;
import cn.slainc.modules.upms.dto.RoleUpdateRequest;
import cn.slainc.modules.upms.dto.RoleUsersRequest;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.service.RoleService;
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
@RequestMapping("/upms/role")
public class RoleManageController {

    private final RoleService roleService;

    public RoleManageController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_CREATE)
    public RoleRecord create(@RequestBody RoleCreateRequest request) {
        return roleService.create(request.code(), request.name(), request.parentId(), request.dataScope(),
                request.sort(), request.tags(), request.remark());
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_UPDATE)
    public RoleRecord update(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        return roleService.update(id, request.name(), request.parentId(), request.dataScope(), request.sort(),
                request.status(), request.tags(), request.remark());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_DELETE)
    public void delete(@PathVariable Long id) {
        roleService.delete(id);
    }

    @GetMapping("/page")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public Object page(@RequestParam(required = false) String name, @RequestParam(required = false) String code,
                       @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return roleService.search(name, code, status, current, size);
    }

    @GetMapping("/list")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public List<RoleRecord> list() {
        return roleService.list();
    }

    @GetMapping("/tree")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public List<RoleRecord> tree() {
        return roleService.tree();
    }

    @PostMapping("/{id}/copy")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_CREATE)
    public RoleRecord copy(@PathVariable Long id, @RequestParam(required = false) String newCode,
                           @RequestParam(required = false) String newName) {
        return roleService.copy(id, newCode, newName);
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_ASSIGN_PERMISSION)
    public void assignPermissions(@PathVariable Long id, @RequestBody RolePermissionRequest request) {
        roleService.assignPermissions(id, request.permissionIds());
    }

    @GetMapping("/{id}/permissions")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public List<Long> permissions(@PathVariable Long id) {
        return roleService.permissionIdsOf(id);
    }

    @PutMapping("/{id}/users")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_ASSIGN_USER)
    public void assignUsers(@PathVariable Long id, @RequestBody RoleUsersRequest request) {
        roleService.assignUsers(id, request.userIds());
    }

    @GetMapping("/{id}/users")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public List<Long> users(@PathVariable Long id) {
        return roleService.userIdsOf(id);
    }

    @PutMapping("/{id}/data-scope")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_DATA_SCOPE)
    public void setDataScope(@PathVariable Long id, @RequestBody DataScopeRequest request) {
        roleService.setDataScope(id, request.dataScope(), request.deptIds());
    }
}
