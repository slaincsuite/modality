package cn.slainc.modules.upms.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.entity.UserGroupMemberRecord;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserGroupMapper;
import cn.slainc.modules.upms.mapper.UserGroupMemberMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UserGroupService {

    private final UserGroupMapper groupMapper;

    private final UserGroupMemberMapper memberMapper;

    private final UserMapper userMapper;

    public UserGroupService(UserGroupMapper groupMapper, UserGroupMemberMapper memberMapper, UserMapper userMapper) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
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
        groupMapper.deleteById(id);
        memberMapper.delete(new LambdaQueryWrapper<UserGroupMemberRecord>()
                .eq(UserGroupMemberRecord::getGroupId, id));
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
        }
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

    public List<UserGroupRecord> groupsOf(Long userId) {
        List<Long> groupIds = memberMapper.selectList(
                        new LambdaQueryWrapper<UserGroupMemberRecord>().eq(UserGroupMemberRecord::getUserId, userId))
                .stream().map(UserGroupMemberRecord::getGroupId).toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return groupMapper.selectList(new LambdaQueryWrapper<UserGroupRecord>().in(UserGroupRecord::getId, groupIds));
    }

    public UserGroupRecord require(Long id) {
        UserGroupRecord group = groupMapper.selectById(id);
        if (group == null) {
            throw new BizException("用户组不存在: " + id);
        }
        return group;
    }
}
