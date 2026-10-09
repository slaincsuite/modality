package cn.slainc.modules.upms.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.RolePermissionRecord;
import cn.slainc.modules.upms.entity.UserRoleRecord;
import cn.slainc.modules.upms.mapper.PermissionMapper;
import cn.slainc.modules.upms.mapper.RolePermissionMapper;
import cn.slainc.modules.upms.mapper.UserRoleMapper;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PermissionService {

    private final PermissionMapper permissionMapper;

    private final RolePermissionMapper rolePermissionMapper;

    private final UserRoleMapper userRoleMapper;

    private final UpmsPermissionCacheInvalidator cacheInvalidator;

    public PermissionService(PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper,
                             UserRoleMapper userRoleMapper, UpmsPermissionCacheInvalidator cacheInvalidator) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.cacheInvalidator = cacheInvalidator;
    }

    public PermissionRecord create(String code, String name, String type, Long parentId, Integer sort, String remark) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new BizException("权限编码与名称不能为空");
        }
        requireType(type == null ? PermissionRecord.TYPE_MENU : type);
        requireParentAlive(parentId);
        long now = System.currentTimeMillis();
        PermissionRecord permission = new PermissionRecord();
        permission.setCode(code.trim());
        permission.setName(name.trim());
        permission.setType(type == null ? PermissionRecord.TYPE_MENU : type);
        permission.setParentId(parentId == null ? 0L : parentId);
        permission.setSort(sort == null ? 0 : sort);
        permission.setStatus(UpmsKeys.STATUS_ENABLED);
        permission.setRemark(remark);
        permission.setCreateTime(now);
        permission.setUpdateTime(now);
        try {
            permissionMapper.insert(permission);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException("权限编码 " + code + " 已存在");
        }
        return permission;
    }

    public PermissionRecord update(Long id, String name, String type, Long parentId, Integer sort, String status,
                                   String remark) {
        PermissionRecord permission = require(id);
        if (name != null && !name.isBlank()) {
            permission.setName(name.trim());
        }
        if (type != null) {
            requireType(type);
            permission.setType(type);
        }
        if (parentId != null) {
            SubtreeUtils.requireValidParent(collectSubtreeIds(id), id, parentId, "权限");
            requireParentAlive(parentId);
            permission.setParentId(parentId);
        }
        if (sort != null) {
            permission.setSort(sort);
        }
        if (status != null) {
            if (!UpmsKeys.STATUS_ENABLED.equals(status) && !UpmsKeys.STATUS_DISABLED.equals(status)) {
                throw new BizException("权限状态只允许 enabled、disabled");
            }
            permission.setStatus(status);
        }
        if (remark != null) {
            permission.setRemark(remark);
        }
        permission.setUpdateTime(System.currentTimeMillis());
        permissionMapper.updateById(permission);
        invalidateHoldersOf(id);
        return permission;
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        long children = permissionMapper.selectCount(new LambdaQueryWrapper<PermissionRecord>()
                .eq(PermissionRecord::getParentId, id));
        if (children > 0) {
            throw new BizException("该权限存在 " + children + " 个下级，先删除或迁移下级后再删除");
        }
        List<Long> holderRoles = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermissionRecord>()
                        .eq(RolePermissionRecord::getPermissionId, id))
                .stream().map(RolePermissionRecord::getRoleId).distinct().toList();
        permissionMapper.deleteById(id);
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionRecord>()
                .eq(RolePermissionRecord::getPermissionId, id));
        invalidateUsersOfRoles(holderRoles);
    }

    public List<PermissionRecord> search(String keyword, String type) {
        LambdaQueryWrapper<PermissionRecord> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(PermissionRecord::getName, keyword.trim())
                    .or().like(PermissionRecord::getCode, keyword.trim()));
        }
        wrapper.eq(type != null && !type.isBlank(), PermissionRecord::getType, type);
        return permissionMapper.selectList(wrapper.orderByAsc(PermissionRecord::getSort)
                .orderByAsc(PermissionRecord::getId));
    }

    public List<PermissionRecord> tree() {
        return childrenOf(search(null, null), 0L);
    }

    public PermissionRecord require(Long id) {
        PermissionRecord permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BizException("权限不存在: " + id);
        }
        return permission;
    }

    // 权限定义全局共享，变更后按引用角色反查持有用户失效缓存；查不到的用户最迟一个缓存 TTL 自然过期
    private void invalidateHoldersOf(Long permissionId) {
        List<Long> roleIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermissionRecord>()
                        .eq(RolePermissionRecord::getPermissionId, permissionId))
                .stream().map(RolePermissionRecord::getRoleId).distinct().toList();
        invalidateUsersOfRoles(roleIds);
    }

    private void invalidateUsersOfRoles(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        Set<Long> userIds = new LinkedHashSet<>();
        userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleRecord>()
                        .in(UserRoleRecord::getRoleId, roleIds))
                .forEach(binding -> userIds.add(binding.getUserId()));
        cacheInvalidator.invalidateUsers(new ArrayList<>(userIds));
    }

    public Set<Long> collectSubtreeIds(Long permissionId) {
        return SubtreeUtils.collectSubtree(permissionId, id -> permissionMapper.selectList(
                        new LambdaQueryWrapper<PermissionRecord>().eq(PermissionRecord::getParentId, id))
                .stream().map(PermissionRecord::getId).toList());
    }

    private List<PermissionRecord> childrenOf(List<PermissionRecord> all, Long parentId) {
        List<PermissionRecord> children = new ArrayList<>();
        for (PermissionRecord permission : all) {
            if (parentId.equals(permission.getParentId() == null ? 0L : permission.getParentId())) {
                children.add(permission);
            }
        }
        return children;
    }

    private void requireParentAlive(Long parentId) {
        if (parentId != null && parentId != 0 && permissionMapper.selectById(parentId) == null) {
            throw new BizException("上级权限不存在: " + parentId);
        }
    }

    private void requireType(String type) {
        if (!PermissionRecord.TYPE_MENU.equals(type) && !PermissionRecord.TYPE_BUTTON.equals(type)
                && !PermissionRecord.TYPE_API.equals(type) && !PermissionRecord.TYPE_DATA.equals(type)) {
            throw new BizException("非法的权限类型: " + type + "，只允许 MENU、BUTTON、API、DATA");
        }
    }
}
