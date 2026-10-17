package cn.slainc.modules.upms.service.role;

import cn.slainc.modules.upms.service.common.SubtreeUtils;
import cn.slainc.modules.upms.service.permission.PermissionDelegationGuard;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.dto.RolePermissionSourceView;
import cn.slainc.modules.upms.dto.RoleUserBindingView;
import cn.slainc.modules.upms.entity.DataScopeType;
import cn.slainc.modules.upms.entity.PolicyBindingRecord;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.RoleDataScopeRecord;
import cn.slainc.modules.upms.entity.RoleGroupRecord;
import cn.slainc.modules.upms.entity.RolePermissionRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserGroupMemberRecord;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.entity.UserRoleRecord;
import cn.slainc.modules.upms.mapper.PolicyBindingMapper;
import cn.slainc.modules.upms.mapper.PermissionMapper;
import cn.slainc.modules.upms.mapper.RoleDataScopeMapper;
import cn.slainc.modules.upms.mapper.RoleGroupMapper;
import cn.slainc.modules.upms.mapper.RoleMapper;
import cn.slainc.modules.upms.mapper.RolePermissionMapper;
import cn.slainc.modules.upms.mapper.UserGroupMapper;
import cn.slainc.modules.upms.mapper.UserGroupMemberMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.mapper.UserRoleMapper;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private static final int MAX_INHERIT_DEPTH = 16;

    private final RoleMapper roleMapper;

    private final RolePermissionMapper rolePermissionMapper;

    private final UserRoleMapper userRoleMapper;

    private final RoleDataScopeMapper roleDataScopeMapper;

    private final RoleGroupMapper roleGroupMapper;

    private final UserGroupMapper groupMapper;

    private final UserGroupMemberMapper groupMemberMapper;

    private final UserMapper userMapper;

    private final PermissionMapper permissionMapper;

    private final PolicyBindingMapper policyBindingMapper;

    private final UpmsPermissionCacheInvalidator cacheInvalidator;

    private final PermissionDelegationGuard delegationGuard;

    public RoleService(RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper, UserRoleMapper userRoleMapper,
                       RoleDataScopeMapper roleDataScopeMapper, RoleGroupMapper roleGroupMapper,
                       UserGroupMapper groupMapper, UserGroupMemberMapper groupMemberMapper,
                       UserMapper userMapper, PermissionMapper permissionMapper,
                       PolicyBindingMapper policyBindingMapper,
                       UpmsPermissionCacheInvalidator cacheInvalidator,
                       PermissionDelegationGuard delegationGuard) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.policyBindingMapper = policyBindingMapper;
        this.cacheInvalidator = cacheInvalidator;
        this.delegationGuard = delegationGuard;
    }

    public RoleRecord create(String code, String name, Long parentId, String dataScope, Integer sort, String tags,
                             String remark) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new BizException("角色编码与名称不能为空");
        }
        DataScopeType.of(dataScope == null ? DataScopeType.SELF.name() : dataScope);
        requireParentAlive(parentId);
        long now = System.currentTimeMillis();
        RoleRecord role = new RoleRecord();
        role.setTenantId(TenantContext.getOrDefault());
        role.setCode(code.trim());
        role.setName(name.trim());
        role.setParentId(parentId == null ? 0L : parentId);
        role.setDataScope(DataScopeType.of(dataScope == null ? DataScopeType.SELF.name() : dataScope).name());
        role.setSort(sort == null ? 0 : sort);
        role.setStatus(UpmsKeys.STATUS_ENABLED);
        role.setTags(tags);
        role.setRemark(remark);
        role.setCreateTime(now);
        role.setUpdateTime(now);
        try {
            roleMapper.insert(role);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException("角色编码 " + code + " 在当前租户下已存在");
        }
        return role;
    }

    public RoleRecord update(Long id, String name, Long parentId, String dataScope, Integer sort, String status,
                             String tags, String remark) {
        RoleRecord role = require(id);
        if (name != null && !name.isBlank()) {
            role.setName(name.trim());
        }
        if (parentId != null) {
            SubtreeUtils.requireValidParent(collectSubtreeIds(id), id, parentId, "角色");
            requireParentAlive(parentId);
            role.setParentId(parentId);
        }
        if (dataScope != null) {
            role.setDataScope(DataScopeType.of(dataScope).name());
        }
        if (sort != null) {
            role.setSort(sort);
        }
        if (status != null) {
            if (!UpmsKeys.STATUS_ENABLED.equals(status) && !UpmsKeys.STATUS_DISABLED.equals(status)) {
                throw new BizException("角色状态只允许 enabled、disabled");
            }
            role.setStatus(status);
        }
        if (tags != null) {
            role.setTags(tags);
        }
        if (remark != null) {
            role.setRemark(remark);
        }
        role.setUpdateTime(System.currentTimeMillis());
        roleMapper.updateById(role);
        cacheInvalidator.invalidateUsers(new ArrayList<>(effectiveUserIdsOf(id)));
        return role;
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        Set<Long> users = effectiveUserIdsOf(id);
        for (RoleRecord child : roleMapper.selectList(
                new LambdaQueryWrapper<RoleRecord>().eq(RoleRecord::getParentId, id))) {
            child.setParentId(0L);
            roleMapper.updateById(child);
        }
        roleMapper.deleteById(id);
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionRecord>()
                .eq(RolePermissionRecord::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getRoleId, id));
        roleDataScopeMapper.delete(new LambdaQueryWrapper<RoleDataScopeRecord>()
                .eq(RoleDataScopeRecord::getRoleId, id));
        roleGroupMapper.delete(new LambdaQueryWrapper<RoleGroupRecord>().eq(RoleGroupRecord::getRoleId, id));
        cacheInvalidator.invalidateUsers(new ArrayList<>(users));
    }

    @Transactional
    public RoleRecord copy(Long id, String newCode, String newName) {
        RoleRecord source = require(id);
        String code = newCode == null || newCode.isBlank() ? source.getCode() + "-copy" : newCode.trim();
        String name = newName == null || newName.isBlank() ? source.getName() + " 副本" : newName.trim();
        RoleRecord copy = create(code, name, source.getParentId(), source.getDataScope(), source.getSort(),
                source.getTags(), "复制自角色 " + source.getCode());
        assignPermissions(copy.getId(), permissionIdsOf(id));
        List<Long> deptIds = roleDataScopeMapper.selectList(new LambdaQueryWrapper<RoleDataScopeRecord>()
                        .eq(RoleDataScopeRecord::getRoleId, id))
                .stream().map(RoleDataScopeRecord::getDeptId).toList();
        if (!deptIds.isEmpty()) {
            setDataScope(copy.getId(), source.getDataScope(), deptIds);
        }
        return copy;
    }

    public PageResult<RoleRecord> search(String name, String code, String status, long current, long size) {
        String safeName = safeTrim(name);
        String safeCode = safeTrim(code);
        Page<RoleRecord> page = roleMapper.selectPage(Page.of(Math.max(1, current), Math.min(Math.max(1, size), 200)),
                new LambdaQueryWrapper<RoleRecord>()
                        .like(safeName != null && !safeName.isBlank(), RoleRecord::getName, safeName)
                        .like(safeCode != null && !safeCode.isBlank(), RoleRecord::getCode, safeCode)
                        .eq(status != null && !status.isBlank(), RoleRecord::getStatus, status)
                        .orderByAsc(RoleRecord::getSort).orderByAsc(RoleRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<RoleRecord> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleRecord>()
                .orderByAsc(RoleRecord::getSort).orderByAsc(RoleRecord::getId));
    }

    public List<RoleRecord> tree() {
        return childrenOf(list(), 0L);
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        RoleRecord role = require(roleId);
        List<Long> target = permissionIds == null ? List.of() : permissionIds;
        delegationGuard.requireGrantablePermissions(target);
        List<RolePermissionRecord> existing = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionRecord>().eq(RolePermissionRecord::getRoleId, roleId));
        Set<Long> keep = new LinkedHashSet<>(target);
        for (RolePermissionRecord binding : existing) {
            if (!keep.contains(binding.getPermissionId())) {
                rolePermissionMapper.deleteById(binding.getId());
            } else {
                keep.remove(binding.getPermissionId());
            }
        }
        long now = System.currentTimeMillis();
        for (Long permissionId : keep) {
            RolePermissionRecord binding = new RolePermissionRecord();
            binding.setTenantId(role.getTenantId());
            binding.setRoleId(roleId);
            binding.setPermissionId(permissionId);
            binding.setCreateTime(now);
            rolePermissionMapper.insert(binding);
        }
        cacheInvalidator.invalidateUsers(new ArrayList<>(effectiveUserIdsOf(roleId)));
    }

    public List<Long> permissionIdsOf(Long roleId) {
        return rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermissionRecord>().eq(RolePermissionRecord::getRoleId, roleId))
                .stream().map(RolePermissionRecord::getPermissionId).toList();
    }

    @Transactional
    public void assignUsers(Long roleId, List<Long> userIds) {
        require(roleId);
        List<Long> target = userIds == null ? List.of() : userIds;
        List<UserRoleRecord> existing = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getRoleId, roleId));
        Set<Long> keep = new LinkedHashSet<>(target);
        for (UserRoleRecord binding : existing) {
            if (!keep.contains(binding.getUserId())) {
                userRoleMapper.deleteById(binding.getId());
            } else {
                keep.remove(binding.getUserId());
            }
        }
        long now = System.currentTimeMillis();
        for (Long userId : keep) {
            UserRoleRecord binding = new UserRoleRecord();
            binding.setTenantId(TenantContext.getOrDefault());
            binding.setUserId(userId);
            binding.setRoleId(roleId);
            binding.setCreateTime(now);
            userRoleMapper.insert(binding);
        }
        cacheInvalidator.invalidateUsers(target);
    }

    public List<Long> userIdsOf(Long roleId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getRoleId, roleId))
                .stream().map(UserRoleRecord::getUserId).toList();
    }

    public List<RoleUserBindingView> userBindingsOf(Long roleId) {
        require(roleId);
        List<UserRoleRecord> bindings = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleRecord>()
                .eq(UserRoleRecord::getRoleId, roleId)
                .orderByDesc(UserRoleRecord::getId));
        if (bindings.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> boundAt = new LinkedHashMap<>();
        bindings.forEach(binding -> boundAt.put(binding.getUserId(), binding.getCreateTime()));
        List<Long> userIds = new ArrayList<>(boundAt.keySet());
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, UserRecord> users = new LinkedHashMap<>();
        userMapper.selectByIds(userIds).forEach(user -> users.put(user.getId(), user));
        return bindings.stream()
                .map(binding -> new RoleUserBindingView(users.get(binding.getUserId()), binding.getCreateTime()))
                .filter(view -> view.user() != null && !Boolean.TRUE.equals(view.user().getDeleted()))
                .toList();
    }

    public List<UserGroupRecord> groupsOf(Long roleId) {
        require(roleId);
        List<Long> groupIds = roleGroupMapper.selectList(new LambdaQueryWrapper<RoleGroupRecord>()
                        .eq(RoleGroupRecord::getRoleId, roleId))
                .stream().map(RoleGroupRecord::getGroupId).toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return groupMapper.selectList(new LambdaQueryWrapper<UserGroupRecord>()
                .in(UserGroupRecord::getId, groupIds).orderByAsc(UserGroupRecord::getId));
    }

    @Transactional
    public void assignGroups(Long roleId, List<Long> groupIds) {
        RoleRecord role = require(roleId);
        delegationGuard.requireAssignableRoles(List.of(roleId));
        List<Long> target = groupIds == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(groupIds));
        if (!target.isEmpty()) {
            long count = groupMapper.selectCount(new LambdaQueryWrapper<UserGroupRecord>()
                    .in(UserGroupRecord::getId, target));
            if (count != target.size()) {
                throw new BizException("用户组不存在或不属于当前租户");
            }
        }
        List<RoleGroupRecord> existing = roleGroupMapper.selectList(new LambdaQueryWrapper<RoleGroupRecord>()
                .eq(RoleGroupRecord::getRoleId, roleId));
        Set<Long> previousGroups = new LinkedHashSet<>();
        existing.forEach(binding -> previousGroups.add(binding.getGroupId()));
        Set<Long> keep = new LinkedHashSet<>(target);
        for (RoleGroupRecord binding : existing) {
            if (!keep.contains(binding.getGroupId())) {
                roleGroupMapper.deleteById(binding.getId());
            } else {
                keep.remove(binding.getGroupId());
            }
        }
        long now = System.currentTimeMillis();
        for (Long groupId : keep) {
            RoleGroupRecord binding = new RoleGroupRecord();
            binding.setTenantId(role.getTenantId());
            binding.setRoleId(roleId);
            binding.setGroupId(groupId);
            binding.setCreateTime(now);
            roleGroupMapper.insert(binding);
        }
        previousGroups.addAll(target);
        invalidateGroupMembers(previousGroups);
    }

    public List<RoleRecord> rolesOfDept(Long deptId) {
        List<Long> roleIds = roleDataScopeMapper.selectList(new LambdaQueryWrapper<RoleDataScopeRecord>()
                        .eq(RoleDataScopeRecord::getDeptId, deptId))
                .stream().map(RoleDataScopeRecord::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<RoleRecord>().in(RoleRecord::getId, roleIds)
                .orderByAsc(RoleRecord::getSort).orderByAsc(RoleRecord::getId));
    }

    public RolePermissionSourceView permissionSourcesOf(Long roleId) {
        require(roleId);
        List<Long> permissionIds = permissionIdsOf(roleId);
        List<PermissionRecord> permissions = permissionIds.isEmpty() ? List.of()
                : permissionMapper.selectList(new LambdaQueryWrapper<PermissionRecord>()
                        .in(PermissionRecord::getId, permissionIds)
                        .orderByAsc(PermissionRecord::getSort).orderByAsc(PermissionRecord::getId));
        List<PolicyBindingRecord> policies = policyBindingMapper.selectList(
                new LambdaQueryWrapper<PolicyBindingRecord>()
                        .eq(PolicyBindingRecord::getSubjectType, PolicyBindingRecord.SUBJECT_ROLE)
                        .eq(PolicyBindingRecord::getSubjectId, String.valueOf(roleId))
                        .orderByDesc(PolicyBindingRecord::getId));
        return new RolePermissionSourceView(permissions, policies);
    }

    @Transactional
    public void setDataScope(Long roleId, String dataScope, List<Long> deptIds) {
        RoleRecord role = require(roleId);
        DataScopeType type = DataScopeType.of(dataScope == null ? role.getDataScope() : dataScope);
        role.setDataScope(type.name());
        role.setUpdateTime(System.currentTimeMillis());
        roleMapper.updateById(role);
        roleDataScopeMapper.delete(new LambdaQueryWrapper<RoleDataScopeRecord>()
                .eq(RoleDataScopeRecord::getRoleId, roleId));
        if (type == DataScopeType.CUSTOM && deptIds != null && !deptIds.isEmpty()) {
            for (Long deptId : deptIds) {
                RoleDataScopeRecord record = new RoleDataScopeRecord();
                record.setTenantId(role.getTenantId());
                record.setRoleId(roleId);
                record.setDeptId(deptId);
                roleDataScopeMapper.insert(record);
            }
        }
        cacheInvalidator.invalidateUsers(new ArrayList<>(effectiveUserIdsOf(roleId)));
    }

    public List<Long> customDeptIdsOf(Long roleId) {
        return roleDataScopeMapper.selectList(
                        new LambdaQueryWrapper<RoleDataScopeRecord>().eq(RoleDataScopeRecord::getRoleId, roleId))
                .stream().map(RoleDataScopeRecord::getDeptId).toList();
    }

    // 用户有效角色 = 直绑角色 + 沿 parent 链继承的祖先角色（防环、限深）
    public List<RoleRecord> resolveUserRoles(Long userId) {
        List<RoleRecord> direct = directRolesOf(userId);
        if (direct.isEmpty()) {
            return List.of();
        }
        Map<Long, RoleRecord> all = new LinkedHashMap<>();
        for (RoleRecord role : roleMapper.selectList(null)) {
            all.put(role.getId(), role);
        }
        Map<Long, RoleRecord> merged = new LinkedHashMap<>();
        for (RoleRecord role : direct) {
            collectWithAncestors(role, all, merged);
        }
        return merged.values().stream().filter(RoleRecord::enabled).toList();
    }

    // 含自身的整棵子树，update 前的成环判定与 delete 前的下级清点共用
    public Set<Long> collectSubtreeIds(Long roleId) {
        return SubtreeUtils.collectSubtree(roleId, id -> roleMapper.selectList(
                        new LambdaQueryWrapper<RoleRecord>().eq(RoleRecord::getParentId, id))
                .stream().map(RoleRecord::getId).toList());
    }

    public List<String> resolveRoleCodes(Long userId) {
        return resolveUserRoles(userId).stream().map(RoleRecord::getCode).toList();
    }

    public List<String> resolvePermissionCodes(Long userId) {
        Set<Long> roleIds = new LinkedHashSet<>();
        for (RoleRecord role : resolveUserRoles(userId)) {
            roleIds.add(role.getId());
        }
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> permissionIds = new LinkedHashSet<>();
        for (RolePermissionRecord binding : rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionRecord>().in(RolePermissionRecord::getRoleId, roleIds))) {
            permissionIds.add(binding.getPermissionId());
        }
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionRecord>()
                        .in(PermissionRecord::getId, permissionIds)
                        .eq(PermissionRecord::getStatus, UpmsKeys.STATUS_ENABLED))
                .stream().map(PermissionRecord::getCode).toList();
    }

    public RoleRecord require(Long id) {
        RoleRecord role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在: " + id);
        }
        return role;
    }

    public List<RoleRecord> directRolesOf(Long userId) {
        Set<Long> roleIds = new LinkedHashSet<>(userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getUserId, userId))
                .stream().map(UserRoleRecord::getRoleId).toList());
        List<Long> groupIds = groupMemberMapper.selectList(new LambdaQueryWrapper<UserGroupMemberRecord>()
                        .eq(UserGroupMemberRecord::getUserId, userId))
                .stream().map(UserGroupMemberRecord::getGroupId).toList();
        if (!groupIds.isEmpty()) {
            roleGroupMapper.selectList(new LambdaQueryWrapper<RoleGroupRecord>()
                            .in(RoleGroupRecord::getGroupId, groupIds))
                    .forEach(binding -> roleIds.add(binding.getRoleId()));
        }
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<RoleRecord>().in(RoleRecord::getId, roleIds));
    }

    private void collectWithAncestors(RoleRecord role, Map<Long, RoleRecord> all, Map<Long, RoleRecord> merged) {
        RoleRecord current = role;
        int depth = 0;
        while (current != null && depth < MAX_INHERIT_DEPTH) {
            if (merged.containsKey(current.getId())) {
                return;
            }
            merged.put(current.getId(), current);
            current = current.getParentId() == null || current.getParentId() == 0 ? null
                    : all.get(current.getParentId());
            depth++;
        }
        if (depth >= MAX_INHERIT_DEPTH) {
            log.warn("角色继承链超过 {} 层，已截断，请检查是否存在环", MAX_INHERIT_DEPTH);
        }
    }

    private List<RoleRecord> childrenOf(List<RoleRecord> all, Long parentId) {
        List<RoleRecord> children = new ArrayList<>();
        for (RoleRecord role : all) {
            if (parentId.equals(role.getParentId() == null ? 0L : role.getParentId())) {
                children.add(role);
            }
        }
        return children;
    }

    private void requireParentAlive(Long parentId) {
        if (parentId != null && parentId != 0 && roleMapper.selectById(parentId) == null) {
            throw new BizException("上级角色不存在: " + parentId);
        }
    }

    private void invalidateGroupMembers(Set<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return;
        }
        List<Long> userIds = groupMemberMapper.selectList(new LambdaQueryWrapper<UserGroupMemberRecord>()
                        .in(UserGroupMemberRecord::getGroupId, groupIds))
                .stream().map(UserGroupMemberRecord::getUserId).distinct().toList();
        cacheInvalidator.invalidateUsers(userIds);
    }

    private Set<Long> effectiveUserIdsOf(Long roleId) {
        Set<Long> users = new LinkedHashSet<>(userIdsOf(roleId));
        List<Long> groupIds = roleGroupMapper.selectList(new LambdaQueryWrapper<RoleGroupRecord>()
                        .eq(RoleGroupRecord::getRoleId, roleId))
                .stream().map(RoleGroupRecord::getGroupId).toList();
        if (!groupIds.isEmpty()) {
            groupMemberMapper.selectList(new LambdaQueryWrapper<UserGroupMemberRecord>()
                            .in(UserGroupMemberRecord::getGroupId, groupIds))
                    .forEach(member -> users.add(member.getUserId()));
        }
        return users;
    }

    private static String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}
