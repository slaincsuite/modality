package cn.slainc.modules.upms.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.security.cache.SecurityPermissionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

// 缓存与用户表都经 ObjectProvider 懒解析：本 Bean 会被权限提供者与全部服务依赖，构造期直接解析会与 securityPermissionCache 成环
public class UpmsPermissionCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(UpmsPermissionCacheInvalidator.class);

    private final ObjectProvider<SecurityPermissionCache> cache;

    private final ObjectProvider<UserMapper> userMapper;

    public UpmsPermissionCacheInvalidator(ObjectProvider<SecurityPermissionCache> cache,
                                          ObjectProvider<UserMapper> userMapper) {
        this.cache = cache;
        this.userMapper = userMapper;
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

    // 批量失效按各自账号体系分组下发；查不到用户记录的 ID 静默跳过
    public void invalidateUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        SecurityPermissionCache resolved = cache.getIfAvailable();
        UserMapper mapper = userMapper.getIfAvailable();
        if (resolved == null || mapper == null) {
            return;
        }
        int invalidated = 0;
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            UserRecord user = mapper.selectById(userId);
            if (user != null) {
                LoginType type = LoginType.of(user.getLoginType());
                if (type != null) {
                    resolved.invalidate(userId, type);
                    invalidated++;
                }
            }
        }
        if (invalidated > 0) {
            log.info("已失效 {} 个账号的权限缓存", invalidated);
        }
    }
}
