package cn.slainc.modules.upms.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.security.config.SecurityKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UpmsPermissionCacheInvalidatorTest extends UpmsIntegrationTestBase {

    @Autowired
    private UpmsPermissionCacheInvalidator invalidator;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private UserService userService;

    @Test
    void batchInvalidateClearsKeysAcrossLoginTypes() {
        UserRecord tenantUser = userService.create("cache-tenant", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        UserRecord platformUser = userService.create("cache-platform", null, null, null, null, null,
                LoginType.PLATFORM_USER, 1L, "admin");
        String tenantKey = SecurityKeys.permissionKey(LoginType.TENANT_USER.value(), tenantUser.getId());
        String platformKey = SecurityKeys.permissionKey(LoginType.PLATFORM_USER.value(), platformUser.getId());
        String untouchedKey = SecurityKeys.permissionKey(LoginType.TENANT_USER.value(), 987654L);
        redis.opsForValue().set(tenantKey, "{\"permissions\":[]}");
        redis.opsForValue().set(platformKey, "{\"permissions\":[]}");
        redis.opsForValue().set(untouchedKey, "{\"permissions\":[]}");

        invalidator.invalidateUsers(Arrays.asList(tenantUser.getId(), platformUser.getId(), null, 987654L));

        assertThat(Boolean.FALSE.equals(redis.hasKey(tenantKey))).isTrue();
        assertThat(Boolean.FALSE.equals(redis.hasKey(platformKey))).isTrue();
        assertThat(Boolean.FALSE.equals(redis.hasKey(untouchedKey))).isFalse();
    }
}
