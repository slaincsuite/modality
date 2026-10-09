package cn.slainc.modules.upms.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.DeptRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.DeptMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeptService {

    private final DeptMapper deptMapper;

    private final UserMapper userMapper;

    public DeptService(DeptMapper deptMapper, UserMapper userMapper) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
    }

    public DeptRecord create(String name, Long parentId, Long leaderUserId, Integer sort) {
        if (name == null || name.isBlank()) {
            throw new BizException("部门名称不能为空");
        }
        requireParentAlive(parentId);
        long now = System.currentTimeMillis();
        DeptRecord dept = new DeptRecord();
        dept.setTenantId(TenantContext.getOrDefault());
        dept.setParentId(parentId == null ? 0L : parentId);
        dept.setName(name.trim());
        dept.setLeaderUserId(leaderUserId);
        dept.setSort(sort == null ? 0 : sort);
        dept.setStatus(UpmsKeys.STATUS_ENABLED);
        dept.setCreateTime(now);
        dept.setUpdateTime(now);
        deptMapper.insert(dept);
        return dept;
    }

    public DeptRecord update(Long id, String name, Long parentId, Long leaderUserId, Integer sort, String status) {
        DeptRecord dept = require(id);
        if (name != null && !name.isBlank()) {
            dept.setName(name.trim());
        }
        if (parentId != null) {
            SubtreeUtils.requireValidParent(collectSubtreeIds(id), id, parentId, "部门");
            requireParentAlive(parentId);
            dept.setParentId(parentId);
        }
        if (leaderUserId != null) {
            dept.setLeaderUserId(leaderUserId);
        }
        if (sort != null) {
            dept.setSort(sort);
        }
        if (status != null) {
            if (!UpmsKeys.STATUS_ENABLED.equals(status) && !UpmsKeys.STATUS_DISABLED.equals(status)) {
                throw new BizException("部门状态只允许 enabled、disabled");
            }
            dept.setStatus(status);
        }
        dept.setUpdateTime(System.currentTimeMillis());
        deptMapper.updateById(dept);
        return dept;
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        Set<Long> subtree = collectSubtreeIds(id);
        subtree.remove(id);
        if (!subtree.isEmpty()) {
            throw new BizException("该部门存在 " + subtree.size() + " 个下级部门，先删除下级部门");
        }
        long users = userMapper.selectCount(new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getDeptId, id).eq(UserRecord::getDeleted, false));
        if (users > 0) {
            throw new BizException("该部门下仍有 " + users + " 个用户，先迁移用户后再删除");
        }
        deptMapper.deleteById(id);
    }

    public List<DeptRecord> tree() {
        List<DeptRecord> all = deptMapper.selectList(new LambdaQueryWrapper<DeptRecord>()
                .orderByAsc(DeptRecord::getSort).orderByAsc(DeptRecord::getId));
        return childrenOf(all, 0L);
    }

    public PageResult<UserRecord> usersOf(Long deptId, boolean includeChildren, long current, long size) {
        LambdaQueryWrapper<UserRecord> wrapper = new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getDeleted, false);
        if (includeChildren) {
            wrapper.in(UserRecord::getDeptId, collectSubtreeIds(deptId));
        } else {
            wrapper.eq(UserRecord::getDeptId, deptId);
        }
        Page<UserRecord> page = userMapper.selectPage(Page.of(current, size),
                wrapper.orderByDesc(UserRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    // 含自身的整棵子树
    public Set<Long> collectSubtreeIds(Long deptId) {
        return SubtreeUtils.collectSubtree(deptId, id -> deptMapper.selectList(
                        new LambdaQueryWrapper<DeptRecord>().eq(DeptRecord::getParentId, id))
                .stream().map(DeptRecord::getId).toList());
    }

    public DeptRecord require(Long id) {
        DeptRecord dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException("部门不存在: " + id);
        }
        return dept;
    }

    private List<DeptRecord> childrenOf(List<DeptRecord> all, Long parentId) {
        List<DeptRecord> children = new ArrayList<>();
        for (DeptRecord dept : all) {
            if (parentId.equals(dept.getParentId() == null ? 0L : dept.getParentId())) {
                children.add(dept);
            }
        }
        return children;
    }

    private void requireParentAlive(Long parentId) {
        if (parentId != null && parentId != 0 && deptMapper.selectById(parentId) == null) {
            throw new BizException("上级部门不存在: " + parentId);
        }
    }
}
