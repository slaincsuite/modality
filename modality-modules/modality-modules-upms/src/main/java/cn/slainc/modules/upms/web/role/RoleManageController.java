package cn.slainc.modules.upms.web.role;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.modules.upms.dto.DataScopeRequest;
import cn.slainc.modules.upms.dto.RoleDataScopeView;
import cn.slainc.modules.upms.dto.RoleCreateRequest;
import cn.slainc.modules.upms.dto.RoleGroupsRequest;
import cn.slainc.modules.upms.dto.RolePermissionSourceView;
import cn.slainc.modules.upms.dto.RolePermissionRequest;
import cn.slainc.modules.upms.dto.RoleUpdateRequest;
import cn.slainc.modules.upms.dto.RoleUserBindingView;
import cn.slainc.modules.upms.dto.RoleUsersRequest;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.service.role.RoleService;
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
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditOperation;

@Tag(name = "角色管理", description = "角色的创建、授权与分配")
@RestController
@RequestMapping("/upms/role")
public class RoleManageController {

    private final RoleService roleService;

    public RoleManageController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_CREATE)
    @AuditLog(value = "创建角色", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "upms")
    public RoleRecord create(@RequestBody RoleCreateRequest request) {
        return roleService.create(request.code(), request.name(), request.parentId(), request.dataScope(),
                request.sort(), request.tags(), request.remark());
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_UPDATE)
    @AuditLog(value = "修改角色", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "upms")
    public RoleRecord update(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        return roleService.update(id, request.name(), request.parentId(), request.dataScope(), request.sort(),
                request.status(), request.tags(), request.remark());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_DELETE)
    @AuditLog(value = "删除角色", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "upms")
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

    @GetMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public RoleRecord get(@PathVariable Long id) {
        return roleService.require(id);
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
    @AuditLog(value = "复制角色", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "upms")
    public RoleRecord copy(@PathVariable Long id, @RequestParam(required = false) String newCode,
                           @RequestParam(required = false) String newName) {
        return roleService.copy(id, newCode, newName);
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_ASSIGN_PERMISSION)
    @AuditLog(value = "授权角色权限", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "upms")
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
    @AuditLog(value = "分配角色用户", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "upms")
    public void assignUsers(@PathVariable Long id, @RequestBody RoleUsersRequest request) {
        roleService.assignUsers(id, request.userIds());
    }

    @GetMapping("/{id}/users")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public List<Long> users(@PathVariable Long id) {
        return roleService.userIdsOf(id);
    }

    @GetMapping("/{id}/user-bindings")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public List<RoleUserBindingView> userBindings(@PathVariable Long id) {
        return roleService.userBindingsOf(id);
    }

    @GetMapping("/{id}/groups")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public List<UserGroupRecord> groups(@PathVariable Long id) {
        return roleService.groupsOf(id);
    }

    @PutMapping("/{id}/groups")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_ASSIGN_USER)
    @AuditLog(value = "分配角色用户组", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "upms")
    public void assignGroups(@PathVariable Long id, @RequestBody RoleGroupsRequest request) {
        roleService.assignGroups(id, request.groupIds());
    }

    @GetMapping("/{id}/permission-sources")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public RolePermissionSourceView permissionSources(@PathVariable Long id) {
        return roleService.permissionSourcesOf(id);
    }

    @GetMapping("/{id}/data-scope")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_QUERY)
    public RoleDataScopeView dataScope(@PathVariable Long id) {
        RoleRecord role = roleService.require(id);
        return new RoleDataScopeView(role.getDataScope(), roleService.customDeptIdsOf(id));
    }

    @PutMapping("/{id}/data-scope")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ROLE_DATA_SCOPE)
    @AuditLog(value = "配置角色数据范围", operation = AuditOperation.UPDATE, category = AuditCategory.PERMISSION,
            module = "upms")
    public void setDataScope(@PathVariable Long id, @RequestBody DataScopeRequest request) {
        roleService.setDataScope(id, request.dataScope(), request.deptIds());
    }
}
