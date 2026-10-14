package cn.slainc.modules.upms.service.user;

import cn.slainc.modules.upms.service.restriction.DeviceRestrictionMatcher;
import cn.slainc.modules.upms.service.restriction.IpRestrictionMatcher;
import cn.slainc.modules.upms.service.restriction.TimeRestrictionMatcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.UserCreatedEvent;
import cn.slainc.core.event.UserUpdatedEvent;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.config.UpmsProperties;
import cn.slainc.modules.upms.entity.AccessControlRecord;
import cn.slainc.modules.upms.entity.PolicyBindingRecord;
import cn.slainc.modules.upms.entity.UserGroupMemberRecord;
import cn.slainc.modules.upms.entity.UserLoginRecordRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.entity.UserRoleRecord;
import cn.slainc.modules.upms.mapper.AccessControlMapper;
import cn.slainc.modules.upms.mapper.PolicyBindingMapper;
import cn.slainc.modules.upms.mapper.UserGroupMemberMapper;
import cn.slainc.modules.upms.mapper.UserLoginRecordMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.mapper.UserRoleMapper;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import cn.slainc.policy.support.PolicyBindingService;
import cn.slainc.security.kick.KickService;

public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String DELETED_USERNAME_MARK = "#deleted#";

    private static final int USERNAME_COLUMN_LENGTH = 64;

    private final UserMapper userMapper;

    private final UserRoleMapper userRoleMapper;

    private final UserGroupMemberMapper groupMemberMapper;

    private final AccessControlMapper accessControlMapper;

    private final PolicyBindingMapper policyBindingMapper;

    private final UserLoginRecordMapper loginRecordMapper;

    private final UpmsProperties properties;

    private final UpmsPermissionCacheInvalidator cacheInvalidator;

    private final ApplicationEventPublisher publisher;

    private final KickService kickService;

    private final ObjectProvider<PolicyBindingService> policyBindingService;

    public UserService(UserMapper userMapper, UserRoleMapper userRoleMapper, UserGroupMemberMapper groupMemberMapper,
                       AccessControlMapper accessControlMapper, PolicyBindingMapper policyBindingMapper,
                       UserLoginRecordMapper loginRecordMapper, UpmsProperties properties,
                       UpmsPermissionCacheInvalidator cacheInvalidator, ApplicationEventPublisher publisher) {
        this(userMapper, userRoleMapper, groupMemberMapper, accessControlMapper, policyBindingMapper, loginRecordMapper,
                properties, cacheInvalidator, publisher, null, null);
    }

    public UserService(UserMapper userMapper, UserRoleMapper userRoleMapper, UserGroupMemberMapper groupMemberMapper,
                       AccessControlMapper accessControlMapper, PolicyBindingMapper policyBindingMapper,
                       UserLoginRecordMapper loginRecordMapper, UpmsProperties properties,
                       UpmsPermissionCacheInvalidator cacheInvalidator, ApplicationEventPublisher publisher,
                       KickService kickService, ObjectProvider<PolicyBindingService> policyBindingService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.accessControlMapper = accessControlMapper;
        this.policyBindingMapper = policyBindingMapper;
        this.loginRecordMapper = loginRecordMapper;
        this.properties = properties;
        this.cacheInvalidator = cacheInvalidator;
        this.publisher = publisher;
        this.kickService = kickService;
        this.policyBindingService = policyBindingService;
    }

    public UserRecord create(String username, String nickname, String password, String email, String phone,
                             Long deptId, LoginType loginType, Long tenantId, String operator) {
        if (username == null || username.isBlank()) {
            throw new BizException("用户名不能为空");
        }
        LoginType type = loginType == null ? LoginType.TENANT_USER : loginType;
        Long tenant = tenantId == null ? TenantContext.getOrDefault() : tenantId;
        String rawPassword = password == null || password.isBlank()
                ? properties.getAccount().getDefaultPassword() : password;
        long now = System.currentTimeMillis();
        UserRecord user = new UserRecord();
        user.setTenantId(tenant);
        user.setUsername(username.trim());
        user.setNickname(nickname == null || nickname.isBlank() ? username.trim() : nickname.trim());
        user.setPasswordHash(BCryptUtils.hash(rawPassword));
        user.setEmail(email);
        user.setPhone(phone);
        user.setDeptId(deptId);
        user.setLoginType(type.value());
        user.setStatus(UpmsKeys.STATUS_ENABLED);
        user.setMustChangePassword(password == null || password.isBlank());
        user.setPasswordChangedAt(now);
        user.setDeleted(false);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        try {
            TenantContext.runWith(tenant, () -> userMapper.insert(user));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException("用户名 " + username + " 在 " + type.value() + " 体系下已存在");
        }
        publisher.publishEvent(new UserCreatedEvent(user.getId(), user.getUsername(), operator));
        return user;
    }

    public UserRecord update(Long id, String nickname, String email, String phone, Long deptId, String avatarUrl,
                             String tags, String extJson, String allowedIps, String allowedTimeRange,
                             String allowedDevices) {
        UserRecord user = requireActive(id);
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        if (deptId != null) {
            user.setDeptId(deptId);
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }
        if (tags != null) {
            user.setTags(tags);
        }
        if (extJson != null) {
            user.setExtJson(extJson);
        }
        if (allowedIps != null) {
            user.setAllowedIps(allowedIps);
        }
        if (allowedTimeRange != null) {
            user.setAllowedTimeRange(allowedTimeRange);
        }
        if (allowedDevices != null) {
            user.setAllowedDevices(allowedDevices);
        }
        user.setUpdateTime(System.currentTimeMillis());
        userMapper.updateById(user);
        publisher.publishEvent(new UserUpdatedEvent(user.getId(), user.getUsername(), null));
        return user;
    }

    public UserRecord get(Long id) {
        return requireActive(id);
    }

    public UserRecord findByUsername(String username, LoginType loginType) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getUsername, username)
                .eq(UserRecord::getLoginType, loginType.value())
                .eq(UserRecord::getDeleted, false)
                .last("LIMIT 1"));
    }

    public void changeStatus(Long id, String status) {
        if (!UpmsKeys.STATUS_ENABLED.equals(status) && !UpmsKeys.STATUS_DISABLED.equals(status)
                && !UpmsKeys.STATUS_LOCKED.equals(status)) {
            throw new BizException("非法的用户状态: " + status + "，只允许 enabled、disabled、locked");
        }
        UserRecord user = requireActive(id);
        user.setStatus(status);
        user.setUpdateTime(System.currentTimeMillis());
        userMapper.updateById(user);
        LoginType type = LoginType.of(user.getLoginType());
        if (type != null) {
            cacheInvalidator.invalidateUser(id, type);
        }
        if (!UpmsKeys.STATUS_ENABLED.equals(status)) {
            kickout(user, type, UpmsKeys.STATUS_LOCKED.equals(status) ? "账号已被锁定" : "账号已被禁用");
        }
    }

    @Transactional
    public void delete(Long id) {
        UserRecord user = requireActive(id);
        softDelete(user);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Map<Long, UserRecord> users = userMapper.selectBatchIds(ids).stream()
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .collect(Collectors.toMap(UserRecord::getId, Function.identity()));
        int count = 0;
        for (Long id : ids) {
            UserRecord user = users.get(id);
            if (user != null) {
                softDelete(user);
                count++;
            }
        }
        return count;
    }

    public int batchDisable(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Map<Long, UserRecord> users = userMapper.selectBatchIds(ids).stream()
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .collect(Collectors.toMap(UserRecord::getId, Function.identity()));
        int count = 0;
        for (Long id : ids) {
            UserRecord user = users.get(id);
            if (user == null || UpmsKeys.STATUS_DISABLED.equals(user.getStatus())) {
                continue;
            }
            user.setStatus(UpmsKeys.STATUS_DISABLED);
            user.setUpdateTime(System.currentTimeMillis());
            userMapper.updateById(user);
            LoginType type = LoginType.of(user.getLoginType());
            if (type != null) {
                cacheInvalidator.invalidateUser(id, type);
            }
            kickout(user, type, "账号已被禁用");
            count++;
        }
        return count;
    }

    @Transactional
    public void merge(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) {
            throw new BizException("合并源账号与目标账号不能相同");
        }
        UserRecord source = requireActive(sourceId);
        UserRecord target = requireActive(targetId);
        if (!source.getLoginType().equals(target.getLoginType())) {
            throw new BizException("跨账号体系的账号不能合并");
        }
        Set<Long> targetRoleIds = new LinkedHashSet<>();
        userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getUserId, targetId))
                .forEach(binding -> targetRoleIds.add(binding.getRoleId()));
        List<UserRoleRecord> sourceRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getUserId, sourceId));
        for (UserRoleRecord binding : sourceRoles) {
            if (targetRoleIds.contains(binding.getRoleId())) {
                userRoleMapper.deleteById(binding.getId());
                continue;
            }
            binding.setUserId(targetId);
            userRoleMapper.updateById(binding);
        }
        for (UserGroupMemberRecord member : groupMemberMapper.selectList(
                new LambdaQueryWrapper<UserGroupMemberRecord>().eq(UserGroupMemberRecord::getUserId, sourceId))) {
            Long groupId = member.getGroupId();
            boolean exists = groupMemberMapper.selectCount(new LambdaQueryWrapper<UserGroupMemberRecord>()
                    .eq(UserGroupMemberRecord::getGroupId, groupId)
                    .eq(UserGroupMemberRecord::getUserId, targetId)) > 0;
            if (exists) {
                groupMemberMapper.deleteById(member.getId());
            } else {
                member.setUserId(targetId);
                groupMemberMapper.updateById(member);
            }
        }
        for (AccessControlRecord grant : accessControlMapper.selectList(
                new LambdaQueryWrapper<AccessControlRecord>().eq(AccessControlRecord::getUserId, sourceId))) {
            boolean exists = accessControlMapper.selectCount(new LambdaQueryWrapper<AccessControlRecord>()
                    .eq(AccessControlRecord::getSystemId, grant.getSystemId())
                    .eq(AccessControlRecord::getUserId, targetId)) > 0;
            if (exists) {
                accessControlMapper.deleteById(grant.getId());
            } else {
                grant.setUserId(targetId);
                accessControlMapper.updateById(grant);
            }
        }
        migratePolicyBindings(sourceId, targetId, LoginType.of(source.getLoginType()));
        LoginType type = LoginType.of(source.getLoginType());
        softDelete(source);
        source.setMergedTo(targetId);
        userMapper.updateById(source);
        if (type != null) {
            cacheInvalidator.invalidateUser(sourceId, type);
        }
        cacheInvalidator.invalidateUser(targetId, LoginType.of(target.getLoginType()));
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        UserRecord user = requireActive(userId);
        List<Long> target = roleIds == null ? List.of() : roleIds;
        List<UserRoleRecord> existing = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getUserId, userId));
        Set<Long> keep = new LinkedHashSet<>(target);
        for (UserRoleRecord binding : existing) {
            if (!keep.contains(binding.getRoleId())) {
                userRoleMapper.deleteById(binding.getId());
            } else {
                keep.remove(binding.getRoleId());
            }
        }
        long now = System.currentTimeMillis();
        for (Long roleId : keep) {
            UserRoleRecord binding = new UserRoleRecord();
            binding.setTenantId(user.getTenantId());
            binding.setUserId(userId);
            binding.setRoleId(roleId);
            binding.setCreateTime(now);
            userRoleMapper.insert(binding);
        }
        if (!target.equals(existing.stream().map(UserRoleRecord::getRoleId).toList())) {
            cacheInvalidator.invalidateUser(userId, LoginType.of(user.getLoginType()));
        }
    }

    public void setAvatar(Long id, String avatarUrl) {
        update(id, null, null, null, null, avatarUrl, null, null, null, null, null);
    }

    public PageResult<UserRecord> search(String username, String nickname, String phone, String email, String status,
                                         Long deptId, Long roleId, Long groupId, String loginType,
                                         Long createdFrom, Long createdTo, long current, long size) {
        LambdaQueryWrapper<UserRecord> wrapper = new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getDeleted, false)
                .like(username != null && !username.isBlank(), UserRecord::getUsername, safeTrim(username))
                .like(nickname != null && !nickname.isBlank(), UserRecord::getNickname, safeTrim(nickname))
                .like(phone != null && !phone.isBlank(), UserRecord::getPhone, safeTrim(phone))
                .like(email != null && !email.isBlank(), UserRecord::getEmail, safeTrim(email))
                .eq(status != null && !status.isBlank(), UserRecord::getStatus, status)
                .eq(deptId != null, UserRecord::getDeptId, deptId)
                .eq(loginType != null && !loginType.isBlank(), UserRecord::getLoginType, loginType)
                .ge(createdFrom != null, UserRecord::getCreateTime, createdFrom)
                .le(createdTo != null, UserRecord::getCreateTime, createdTo)
                .orderByDesc(UserRecord::getId);
        if (roleId != null) {
            wrapper.exists("SELECT 1 FROM t_user_role ur WHERE ur.user_id = t_user.id AND ur.role_id = {0}", roleId);
        }
        if (groupId != null) {
            wrapper.exists("SELECT 1 FROM t_user_group_member gm WHERE gm.user_id = t_user.id AND gm.group_id = {0}",
                    groupId);
        }
        Page<UserRecord> page = userMapper.selectPage(Page.of(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<UserLoginRecordRecord> recentLogins(Long userId, int limit) {
        return loginRecordMapper.selectList(new LambdaQueryWrapper<UserLoginRecordRecord>()
                .eq(UserLoginRecordRecord::getUserId, userId)
                .orderByDesc(UserLoginRecordRecord::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    public List<UserRecord> listAll() {
        return userMapper.selectList(new LambdaQueryWrapper<UserRecord>().eq(UserRecord::getDeleted, false));
    }

    public List<Long> roleIdsOf(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getUserId, userId))
                .stream().map(UserRoleRecord::getRoleId).toList();
    }

    public List<Long> userIdsOfRole(Long roleId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getRoleId, roleId))
                .stream().map(UserRoleRecord::getUserId).toList();
    }

    public boolean checkAllowedIp(UserRecord user, String clientIp) {
        return IpRestrictionMatcher.matches(user.getAllowedIps(), clientIp);
    }

    public boolean checkAllowedTime(UserRecord user, long epochMillis) {
        return TimeRestrictionMatcher.matches(user.getAllowedTimeRange(), epochMillis);
    }

    public boolean checkAllowedDevice(UserRecord user, String deviceType) {
        return DeviceRestrictionMatcher.matches(user.getAllowedDevices(), deviceType);
    }

    private void softDelete(UserRecord user) {
        user.setDeleted(true);
        user.setStatus(UpmsKeys.STATUS_DISABLED);
        user.setUsername(releasedUsername(user.getUsername(), user.getId()));
        user.setUpdateTime(System.currentTimeMillis());
        userMapper.updateById(user);
        List<UserRoleRecord> bindings = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleRecord>().eq(UserRoleRecord::getUserId, user.getId()));
        List<Long> bindingIds = bindings.stream().map(UserRoleRecord::getId).toList();
        if (!bindingIds.isEmpty()) {
            userRoleMapper.deleteByIds(bindingIds);
        }
        LoginType type = LoginType.of(user.getLoginType());
        if (type != null) {
            cacheInvalidator.invalidateUser(user.getId(), type);
        }
        kickout(user, type, "账号已注销");
    }

    // 注销行仍留在表内，用户名必须让位给后续重建的同名账号，否则 UNIQUE(login_type, username) 被永久占用
    private static String releasedUsername(String username, Long id) {
        String suffix = DELETED_USERNAME_MARK + id;
        String base = username == null ? "" : username;
        int keep = Math.max(0, USERNAME_COLUMN_LENGTH - suffix.length());
        return (base.length() > keep ? base.substring(0, keep) : base) + suffix;
    }

    // 策略求值按策略本体的 subjectId 命中，仅改绑定行的归属无效，必须解绑后按目标账号重新派生
    private void migratePolicyBindings(Long sourceId, Long targetId, LoginType type) {
        List<PolicyBindingRecord> bindings = policyBindingMapper.selectList(
                new LambdaQueryWrapper<PolicyBindingRecord>()
                        .eq(PolicyBindingRecord::getSubjectType, PolicyBindingRecord.SUBJECT_USER)
                        .eq(PolicyBindingRecord::getSubjectId, String.valueOf(sourceId)));
        if (bindings.isEmpty()) {
            return;
        }
        Set<String> targetPolicyIds = new LinkedHashSet<>();
        policyBindingMapper.selectList(new LambdaQueryWrapper<PolicyBindingRecord>()
                        .eq(PolicyBindingRecord::getSubjectType, PolicyBindingRecord.SUBJECT_USER)
                        .eq(PolicyBindingRecord::getSubjectId, String.valueOf(targetId)))
                .forEach(binding -> targetPolicyIds.add(binding.getPolicyId()));
        PolicyBindingService bindingService = policyBindingService == null ? null : policyBindingService.getIfAvailable();
        for (PolicyBindingRecord binding : bindings) {
            if (targetPolicyIds.contains(binding.getPolicyId())) {
                policyBindingMapper.deleteById(binding.getId());
                continue;
            }
            if (bindingService == null) {
                binding.setSubjectId(String.valueOf(targetId));
                policyBindingMapper.updateById(binding);
                continue;
            }
            try {
                bindingService.bindToUser(binding.getPolicyId(), targetId, type, UserContext.username());
                bindingService.unbind(binding.getId());
            } catch (RuntimeException e) {
                log.warn("迁移账号 {} 的策略绑定 {} 失败，仅改写绑定归属：{}", sourceId, binding.getId(), e.getMessage());
                binding.setSubjectId(String.valueOf(targetId));
                policyBindingMapper.updateById(binding);
            }
        }
    }

    private void kickout(UserRecord user, LoginType type, String reason) {
        if (kickService == null || type == null || user.getId() == null) {
            return;
        }
        try {
            kickService.kickout(type, user.getId(), reason);
        } catch (RuntimeException e) {
            log.warn("账号 {} 的在线会话下线失败，权限缓存已失效：{}", user.getId(), e.getMessage());
        }
    }

    private static String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private UserRecord requireActive(Long id) {
        UserRecord user = userMapper.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new BizException("用户不存在或已注销: " + id);
        }
        return user;
    }

    public List<String> validateRestrictions(UserRecord user, String clientIp, String deviceType) {
        List<String> violations = new ArrayList<>();
        if (!checkAllowedIp(user, clientIp)) {
            violations.add("当前 IP 不在账号允许的 IP 范围内");
        }
        if (!checkAllowedTime(user, System.currentTimeMillis())) {
            violations.add("当前时间不在账号允许的登录时段内");
        }
        if (!checkAllowedDevice(user, deviceType)) {
            violations.add("当前设备类型未被账号允许");
        }
        return violations;
    }
}
