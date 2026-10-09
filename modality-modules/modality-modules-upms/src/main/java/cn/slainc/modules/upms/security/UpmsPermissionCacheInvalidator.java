package cn.slainc.modules.upms.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.security.cache.SecurityPermissionCache;
import cn.slainc.security.config.SecurityKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 缓存与用户表都经 ObjectProvider 懒解析：本 Bean 会被权限提供者与全部服务依赖，构造期直接解析会与 securityPermissionCache 成环
public class UpmsPermissionCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(UpmsPermissionCacheInvalidator.class);

    private final ObjectProvider<SecurityPermissionCache> cache;

    private final ObjectProvider<UserMapper> userMapper;

    private final ObjectProvider<StringRedisTemplate> redis;

    public UpmsPermissionCacheInvalidator(ObjectProvider<SecurityPermissionCache> cache,
                                          ObjectProvider<UserMapper> userMapper) {
        this(cache, userMapper, null);
    }

    public UpmsPermissionCacheInvalidator(ObjectProvider<SecurityPermissionCache> cache,
                                          ObjectProvider<UserMapper> userMapper,
                                          ObjectProvider<StringRedisTemplate> redis) {
        this.cache = cache;
        this.userMapper = userMapper;
        this.redis = redis;
    }

    public void invalidateUser(Long userId, LoginType type) {
        if (userId == null) {
            return;
        }
        SecurityPermissionCache resolved = cache.getIfAvailable();
        if (resolved != null) {
            resolved.invalidate(userId, type);
        }
    }

    // 一次 selectByIds 解析账号体系后按体系分组下发，避免逐用户 selectById + 逐条 DEL 的往返放大
    public void invalidateUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        SecurityPermissionCache resolved = cache.getIfAvailable();
        UserMapper mapper = userMapper.getIfAvailable();
        if (resolved == null || mapper == null) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null) {
                distinct.add(userId);
            }
        }
        if (distinct.isEmpty()) {
            return;
        }
        Map<LoginType, List<Long>> grouped = new LinkedHashMap<>();
        for (UserRecord user : mapper.selectByIds(distinct)) {
            LoginType type = LoginType.of(user.getLoginType());
            if (type != null) {
                grouped.computeIfAbsent(type, key -> new ArrayList<>()).add(user.getId());
            }
        }
        int invalidated = 0;
        for (Map.Entry<LoginType, List<Long>> entry : grouped.entrySet()) {
            List<Long> ids = entry.getValue();
            if (!batchDelete(entry.getKey(), ids)) {
                ids.forEach(id -> resolved.invalidate(id, entry.getKey()));
            }
            invalidated += ids.size();
        }
        if (invalidated > 0) {
            log.info("已失效 {} 个账号的权限缓存", invalidated);
        }
    }

    private boolean batchDelete(LoginType type, List<Long> userIds) {
        StringRedisTemplate template = redis == null ? null : redis.getIfAvailable();
        if (template == null) {
            return false;
        }
        List<String> keys = userIds.stream().map(userId -> SecurityKeys.permissionKey(type.value(), userId)).toList();
        try {
            template.delete(keys);
            return true;
        } catch (RuntimeException e) {
            log.warn("批量清除权限缓存失败，回退逐条删除：{}", e.getMessage());
            return false;
        }
    }
}
