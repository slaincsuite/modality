package cn.slainc.modules.upms.service.permission;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.core.support.PermissionMatcher;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.RolePermissionRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.mapper.PermissionMapper;
import cn.slainc.modules.upms.mapper.RoleMapper;
import cn.slainc.modules.upms.mapper.RolePermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 授权委派不能超过操作者当前有效权限，防止租户管理员通过自建高权角色完成横向或纵向提权
public class PermissionDelegationGuard {

    private static final int MAX_ROLE_DEPTH = 16;

    private final RoleMapper roleMapper;

    private final RolePermissionMapper rolePermissionMapper;

    private final PermissionMapper permissionMapper;

    public PermissionDelegationGuard(RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper,
                                     PermissionMapper permissionMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
    }

    public void requireGrantablePermissions(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        Set<Long> distinctIds = new LinkedHashSet<>(permissionIds);
        List<PermissionRecord> permissions = permissionMapper.selectByIds(distinctIds);
        if (permissions.size() != distinctIds.size()) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "待授予权限中包含不存在的数据");
        }
        if (permissions.stream().anyMatch(permission -> !UpmsKeys.STATUS_ENABLED.equals(permission.getStatus()))) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "待授予权限中包含已停用的数据");
        }
        if (!requiresGuard()) {
            return;
        }
        List<String> codes = permissions.stream().map(PermissionRecord::getCode).toList();
        requireHeld(codes, "授予权限");
    }

    public void requireAssignableRoles(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> effectiveRoleIds = effectiveRoleIds(roleIds);
        if (!effectiveRoleIds.containsAll(roleIds)) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "待分配角色中包含不存在或不属于当前租户的数据");
        }
        if (!requiresGuard()) {
            return;
        }
        Set<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermissionRecord>()
                        .in(RolePermissionRecord::getRoleId, effectiveRoleIds))
                .stream().map(RolePermissionRecord::getPermissionId)
                .collect(java.util.stream.Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return;
        }
        List<String> codes = permissionMapper.selectByIds(permissionIds).stream()
                .filter(permission -> UpmsKeys.STATUS_ENABLED.equals(permission.getStatus()))
                .map(PermissionRecord::getCode).toList();
        requireHeld(codes, "分配角色");
    }

    private Set<Long> effectiveRoleIds(Collection<Long> roleIds) {
        Map<Long, RoleRecord> roles = new LinkedHashMap<>();
        roleMapper.selectList(null).forEach(role -> roles.put(role.getId(), role));
        Set<Long> result = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            RoleRecord current = roles.get(roleId);
            int depth = 0;
            while (current != null && depth++ < MAX_ROLE_DEPTH && result.add(current.getId())) {
                Long parentId = current.getParentId();
                current = parentId == null || parentId == 0 ? null : roles.get(parentId);
            }
        }
        return result;
    }

    private void requireHeld(List<String> requiredCodes, String action) {
        Set<String> missing = new LinkedHashSet<>();
        for (String code : requiredCodes) {
            if (!PermissionMatcher.granted(UserContext.permissions(), code)) {
                missing.add(code);
            }
        }
        if (!missing.isEmpty()) {
            String detail = String.join("、", missing.stream().limit(8).toList());
            throw new BizException(ResultCode.FORBIDDEN, action + "失败，当前账号未持有: " + detail);
        }
    }

    private boolean requiresGuard() {
        return UserContext.isAuthenticated() && !UserContext.isSuperAdmin();
    }
}
