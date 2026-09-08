package cn.slainc.security.cache;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.security.spi.SecurityPermissionProvider;
import cn.slainc.security.spi.SecurityPolicyProvider;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

public class SecurityPermissionCache implements SecurityPermissionCacheRefresher {

    private static final Logger log = LoggerFactory.getLogger(SecurityPermissionCache.class);

    private final StringRedisTemplate redis;

    private final SecurityPermissionProvider permissionProvider;

    private final SecurityPolicyProvider policyProvider;

    private final SecurityConfigReader config;

    public SecurityPermissionCache(StringRedisTemplate redis, SecurityPermissionProvider permissionProvider,
                                   SecurityPolicyProvider policyProvider, SecurityConfigReader config) {
        this.redis = redis;
        this.permissionProvider = permissionProvider;
        this.policyProvider = policyProvider;
        this.config = config;
    }

    public GrantedSet load(Object loginId, LoginType type) {
        String key = SecurityKeys.permissionKey(type.value(), loginId);
        int ttl = config.permissionCacheTtlSeconds();
        if (ttl > 0) {
            try {
                String json = redis.opsForValue().get(key);
                if (json != null) {
                    GrantedSet cached = JsonUtils.parse(json, GrantedSet.class);
                    if (cached != null) {
                        return cached;
                    }
                }
            } catch (Exception e) {
                log.warn("读取权限缓存失败，降级直查权限 SPI：{}", e.getMessage());
            }
        }
        GrantedSet granted = loadDirect(loginId, type);
        if (ttl > 0) {
            try {
                redis.opsForValue().set(key, JsonUtils.toJson(granted), Duration.ofSeconds(ttl));
            } catch (Exception e) {
                log.warn("写入权限缓存失败，本次结果不缓存：{}", e.getMessage());
            }
        }
        return granted;
    }

    public GrantedSet loadDirect(Object loginId, LoginType type) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        List<String> rbacPermissions = permissionProvider.getPermissions(loginId, type);
        if (rbacPermissions != null) {
            permissions.addAll(rbacPermissions);
        }
        List<String> policyPermissions = policyProvider.getPermissions(loginId, type);
        if (policyPermissions != null) {
            permissions.addAll(policyPermissions);
        }
        List<String> rbacRoles = permissionProvider.getRoles(loginId, type);
        if (rbacRoles != null) {
            roles.addAll(rbacRoles);
        }
        List<String> policyRoles = policyProvider.getRoles(loginId, type);
        if (policyRoles != null) {
            roles.addAll(policyRoles);
        }
        return new GrantedSet(List.copyOf(permissions), List.copyOf(roles));
    }

    @Override
    public void invalidate(Object loginId) {
        for (LoginType type : LoginType.values()) {
            invalidate(loginId, type);
        }
    }

    @Override
    public void invalidate(Object loginId, LoginType type) {
        String key = SecurityKeys.permissionKey(type.value(), loginId);
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("清除权限缓存失败，缓存将在到期后自然失效：{}", e.getMessage());
        }
    }
}
