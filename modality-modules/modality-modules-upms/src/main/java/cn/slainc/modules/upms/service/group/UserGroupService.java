package cn.slainc.modules.upms.service.group;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.dto.GroupMemberBindingView;
import cn.slainc.modules.upms.dto.UserGroupBindingView;
import cn.slainc.modules.upms.entity.RoleGroupRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserGroupMemberRecord;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.RoleGroupMapper;
import cn.slainc.modules.upms.mapper.RoleMapper;
import cn.slainc.modules.upms.mapper.UserGroupMapper;
import cn.slainc.modules.upms.mapper.UserGroupMemberMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserGroupService {

    private final UserGroupMapper groupMapper;

    private final UserGroupMemberMapper memberMapper;

    private final UserMapper userMapper;

    private final RoleMapper roleMapper;

    private final RoleGroupMapper roleGroupMapper;

    private final UpmsPermissionCacheInvalidator cacheInvalidator;

    public UserGroupService(UserGroupMapper groupMapper, UserGroupMemberMapper memberMapper, UserMapper userMapper,
                            RoleMapper roleMapper, RoleGroupMapper roleGroupMapper,
                            UpmsPermissionCacheInvalidator cacheInvalidator) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.cacheInvalidator = cacheInvalidator;
    }

    public UserGroupRecord create(String name, String remark) {
        if (name == null || name.isBlank()) {
            throw new BizException("用户组名称不能为空");
        }
        long now = System.currentTimeMillis();
        UserGroupRecord group = new UserGroupRecord();
        group.setTenantId(TenantContext.getOrDefault());
        group.setName(name.trim());
        group.setRemark(remark);
        group.setCreateTime(now);
        group.setUpdateTime(now);
        groupMapper.insert(group);
        return group;
    }

    public UserGroupRecord update(Long id, String name, String remark) {
        UserGroupRecord group = require(id);
        if (name != null && !name.isBlank()) {
            group.setName(name.trim());
        }
        if (remark != null) {
            group.setRemark(remark);
        }
        group.setUpdateTime(System.currentTimeMillis());
        groupMapper.updateById(group);
        return group;
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        List<Long> affectedUserIds = memberIdsOf(id);
        groupMapper.deleteById(id);
        memberMapper.delete(new LambdaQueryWrapper<UserGroupMemberRecord>()
                .eq(UserGroupMemberRecord::getGroupId, id));
        roleGroupMapper.delete(new LambdaQueryWrapper<RoleGroupRecord>()
                .eq(RoleGroupRecord::getGroupId, id));
        cacheInvalidator.invalidateUsers(affectedUserIds);
    }

    public List<UserGroupRecord> list() {
        return groupMapper.selectList(new LambdaQueryWrapper<UserGroupRecord>()
                .orderByAsc(UserGroupRecord::getId));
    }

    @Transactional
    public void assignMembers(Long groupId, List<Long> userIds) {
        require(groupId);
        List<Long> target = userIds == null ? List.of() : userIds;
        List<UserGroupMemberRecord> existing = memberMapper.selectList(
                new LambdaQueryWrapper<UserGroupMemberRecord>().eq(UserGroupMemberRecord::getGroupId, groupId));
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        existing.forEach(member -> affectedUserIds.add(member.getUserId()));
        Set<Long> keep = new LinkedHashSet<>(target);
        for (UserGroupMemberRecord member : existing) {
            if (!keep.contains(member.getUserId())) {
                memberMapper.deleteById(member.getId());
            } else {
                keep.remove(member.getUserId());
            }
        }
        long now = System.currentTimeMillis();
        for (Long userId : keep) {
            UserGroupMemberRecord member = new UserGroupMemberRecord();
            member.setTenantId(TenantContext.getOrDefault());
            member.setGroupId(groupId);
            member.setUserId(userId);
            member.setCreateTime(now);
            memberMapper.insert(member);
            affectedUserIds.add(userId);
        }
        cacheInvalidator.invalidateUsers(affectedUserIds.stream().toList());
    }

    public List<Long> memberIdsOf(Long groupId) {
        return memberMapper.selectList(new LambdaQueryWrapper<UserGroupMemberRecord>()
                        .eq(UserGroupMemberRecord::getGroupId, groupId))
                .stream().map(UserGroupMemberRecord::getUserId).toList();
    }

    public List<UserRecord> membersOf(Long groupId) {
        List<Long> userIds = memberIdsOf(groupId);
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<UserRecord>()
                .in(UserRecord::getId, userIds).eq(UserRecord::getDeleted, false));
    }

    public List<GroupMemberBindingView> memberBindingsOf(Long groupId) {
        require(groupId);
        List<UserGroupMemberRecord> bindings = memberMapper.selectList(
                new LambdaQueryWrapper<UserGroupMemberRecord>()
                        .eq(UserGroupMemberRecord::getGroupId, groupId)
                        .orderByDesc(UserGroupMemberRecord::getId));
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = bindings.stream().map(UserGroupMemberRecord::getUserId).distinct().toList();
        Map<Long, UserRecord> users = new LinkedHashMap<>();
        userMapper.selectByIds(userIds).forEach(user -> users.put(user.getId(), user));
        return bindings.stream()
                .map(binding -> new GroupMemberBindingView(users.get(binding.getUserId()), binding.getCreateTime()))
                .filter(view -> view.user() != null && !Boolean.TRUE.equals(view.user().getDeleted()))
                .toList();
    }

    public List<UserGroupRecord> groupsOf(Long userId) {
        List<Long> groupIds = memberMapper.selectList(
                        new LambdaQueryWrapper<UserGroupMemberRecord>().eq(UserGroupMemberRecord::getUserId, userId))
                .stream().map(UserGroupMemberRecord::getGroupId).toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return groupMapper.selectList(new LambdaQueryWrapper<UserGroupRecord>().in(UserGroupRecord::getId, groupIds));
    }

    public List<UserGroupBindingView> groupBindingsOf(Long userId) {
        List<UserGroupMemberRecord> bindings = memberMapper.selectList(
                new LambdaQueryWrapper<UserGroupMemberRecord>()
                        .eq(UserGroupMemberRecord::getUserId, userId)
                        .orderByDesc(UserGroupMemberRecord::getId));
        if (bindings.isEmpty()) {
            return List.of();
        }
        Map<Long, UserGroupRecord> groups = new LinkedHashMap<>();
        groupMapper.selectByIds(bindings.stream().map(UserGroupMemberRecord::getGroupId).distinct().toList())
                .forEach(group -> groups.put(group.getId(), group));
        return bindings.stream()
                .map(binding -> new UserGroupBindingView(groups.get(binding.getGroupId()), binding.getCreateTime()))
                .filter(view -> view.group() != null)
                .toList();
    }

    public List<RoleRecord> rolesOf(Long groupId) {
        require(groupId);
        List<Long> roleIds = roleGroupMapper.selectList(new LambdaQueryWrapper<RoleGroupRecord>()
                        .eq(RoleGroupRecord::getGroupId, groupId))
                .stream().map(RoleGroupRecord::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<RoleRecord>().in(RoleRecord::getId, roleIds)
                .orderByAsc(RoleRecord::getSort).orderByAsc(RoleRecord::getId));
    }

    @Transactional
    public void assignRoles(Long groupId, List<Long> roleIds) {
        UserGroupRecord group = require(groupId);
        List<Long> target = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        if (!target.isEmpty()) {
            long count = roleMapper.selectCount(new LambdaQueryWrapper<RoleRecord>()
                    .in(RoleRecord::getId, target));
            if (count != target.size()) {
                throw new BizException("角色不存在或不属于当前租户");
            }
        }
        List<RoleGroupRecord> existing = roleGroupMapper.selectList(new LambdaQueryWrapper<RoleGroupRecord>()
                .eq(RoleGroupRecord::getGroupId, groupId));
        Set<Long> keep = new LinkedHashSet<>(target);
        for (RoleGroupRecord binding : existing) {
            if (!keep.contains(binding.getRoleId())) {
                roleGroupMapper.deleteById(binding.getId());
            } else {
                keep.remove(binding.getRoleId());
            }
        }
        long now = System.currentTimeMillis();
        for (Long roleId : keep) {
            RoleGroupRecord binding = new RoleGroupRecord();
            binding.setTenantId(group.getTenantId());
            binding.setRoleId(roleId);
            binding.setGroupId(groupId);
            binding.setCreateTime(now);
            roleGroupMapper.insert(binding);
        }
        cacheInvalidator.invalidateUsers(memberIdsOf(groupId));
    }

    public UserGroupRecord require(Long id) {
        UserGroupRecord group = groupMapper.selectById(id);
        if (group == null) {
            throw new BizException("用户组不存在: " + id);
        }
        return group;
    }
}
