package cn.slainc.modules.upms.service.user;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.dto.PermissionBoundaryRequest;
import cn.slainc.modules.upms.dto.PermissionBoundaryView;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.UserPermissionBoundaryRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.PermissionMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.mapper.UserPermissionBoundaryMapper;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UserPermissionBoundaryService {

    private final UserPermissionBoundaryMapper boundaryMapper;

    private final PermissionMapper permissionMapper;

    private final UserMapper userMapper;

    private final UpmsPermissionCacheInvalidator cacheInvalidator;

    public UserPermissionBoundaryService(UserPermissionBoundaryMapper boundaryMapper, PermissionMapper permissionMapper,
                                         UserMapper userMapper, UpmsPermissionCacheInvalidator cacheInvalidator) {
        this.boundaryMapper = boundaryMapper;
        this.permissionMapper = permissionMapper;
        this.userMapper = userMapper;
        this.cacheInvalidator = cacheInvalidator;
    }

    public PermissionBoundaryView get(Long userId) {
        requireUser(userId);
        List<UserPermissionBoundaryRecord> records = records(userId);
        Long updateTime = records.stream().map(UserPermissionBoundaryRecord::getUpdateTime)
                .filter(value -> value != null).max(Long::compareTo).orElse(null);
        return new PermissionBoundaryView(userId, codes(records, UserPermissionBoundaryRecord.EFFECT_ALLOW),
                codes(records, UserPermissionBoundaryRecord.EFFECT_DENY), updateTime);
    }

    @Transactional
    public PermissionBoundaryView update(Long userId, PermissionBoundaryRequest request) {
        UserRecord user = requireUser(userId);
        Set<String> allowed = normalize(request == null ? null : request.allowedPermissions(), "允许权限");
        Set<String> denied = normalize(request == null ? null : request.deniedPermissions(), "拒绝权限");
        Set<String> overlap = new LinkedHashSet<>(allowed);
        overlap.retainAll(denied);
        if (!overlap.isEmpty()) {
            throw new BizException("同一权限不能同时出现在允许与拒绝集合中: " + String.join(", ", overlap));
        }
        validateCodes(allowed);
        validateCodes(denied);
        boundaryMapper.delete(new LambdaQueryWrapper<UserPermissionBoundaryRecord>()
                .eq(UserPermissionBoundaryRecord::getUserId, userId));
        long now = System.currentTimeMillis();
        for (String code : allowed) {
            insert(user, UserPermissionBoundaryRecord.EFFECT_ALLOW, code, now);
        }
        for (String code : denied) {
            insert(user, UserPermissionBoundaryRecord.EFFECT_DENY, code, now);
        }
        cacheInvalidator.invalidateUser(userId, LoginType.of(user.getLoginType()));
        return new PermissionBoundaryView(userId, new ArrayList<>(allowed), new ArrayList<>(denied), now);
    }

    public List<String> apply(Long userId, List<String> permissions) {
        if (userId == null || permissions == null || permissions.isEmpty()) {
            return permissions == null ? List.of() : permissions;
        }
        List<UserPermissionBoundaryRecord> records = records(userId);
        if (records.isEmpty()) {
            return permissions;
        }
        Set<String> allowed = new LinkedHashSet<>(codes(records, UserPermissionBoundaryRecord.EFFECT_ALLOW));
        Set<String> denied = new LinkedHashSet<>(codes(records, UserPermissionBoundaryRecord.EFFECT_DENY));
        List<String> effective = new ArrayList<>();
        for (String permission : permissions) {
            if (denied.contains(permission) || (!allowed.isEmpty() && !allowed.contains(permission))) {
                continue;
            }
            effective.add(permission);
        }
        return effective;
    }

    public boolean allows(Long userId, String permissionCode) {
        return apply(userId, List.of(permissionCode)).contains(permissionCode);
    }

    private void insert(UserRecord user, String effect, String code, long now) {
        UserPermissionBoundaryRecord record = new UserPermissionBoundaryRecord();
        record.setTenantId(user.getTenantId());
        record.setUserId(user.getId());
        record.setEffect(effect);
        record.setPermissionCode(code);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        boundaryMapper.insert(record);
    }

    private void validateCodes(Set<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        long count = permissionMapper.selectCount(new LambdaQueryWrapper<PermissionRecord>()
                .in(PermissionRecord::getCode, codes));
        if (count != codes.size()) {
            throw new BizException("权限边界包含不存在的权限码");
        }
    }

    private Set<String> normalize(List<String> values, String label) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new BizException(label + "不能包含空值");
            }
            result.add(value.trim());
        }
        return result;
    }

    private List<UserPermissionBoundaryRecord> records(Long userId) {
        return boundaryMapper.selectList(new LambdaQueryWrapper<UserPermissionBoundaryRecord>()
                .eq(UserPermissionBoundaryRecord::getUserId, userId)
                .orderByAsc(UserPermissionBoundaryRecord::getId));
    }

    private List<String> codes(List<UserPermissionBoundaryRecord> records, String effect) {
        return records.stream().filter(record -> effect.equals(record.getEffect()))
                .map(UserPermissionBoundaryRecord::getPermissionCode).toList();
    }

    private UserRecord requireUser(Long userId) {
        UserRecord user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new BizException("用户不存在或已注销: " + userId);
        }
        if (user.getTenantId() != null && !user.getTenantId().equals(TenantContext.getOrDefault())
                && !UserContext.isSuperAdmin()) {
            throw new BizException("无权访问其他租户的用户: " + userId);
        }
        return user;
    }
}
