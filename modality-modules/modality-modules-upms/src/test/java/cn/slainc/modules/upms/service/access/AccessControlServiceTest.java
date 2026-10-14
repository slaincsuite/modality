package cn.slainc.modules.upms.service.access;

import cn.slainc.modules.upms.service.registry.SystemRegistryService;
import cn.slainc.modules.upms.service.user.UserService;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.SystemRegistryRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessControlServiceTest extends UpmsIntegrationTestBase {

    @Autowired
    private SystemRegistryService systemRegistryService;

    @Autowired
    private AccessControlService accessControlService;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void registryValidationsAndAccessCode() {
        assertThatThrownBy(() -> systemRegistryService.create("sys", "系统", null, null, null, null, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("业务模块编码");
        SystemRegistryRecord system = systemRegistryService.create("billing", "计费系统", "finance", "PRIVATE",
                "财务组", "coin", "https://billing.example.com", 1);
        assertThat(system.accessPermissionCode()).isEqualTo("finance:billing:access");
        assertThatThrownBy(() -> systemRegistryService.create("billing", "重复", "finance", null, null, null, null,
                null)).isInstanceOf(BizException.class).hasMessageContaining("已存在");
        assertThatThrownBy(() -> systemRegistryService.changeVisibility(system.getId(), "SECRET"))
                .isInstanceOf(BizException.class).hasMessageContaining("PUBLIC、PRIVATE");
    }

    @Test
    void publicSystemNeedsNoGrant() {
        SystemRegistryRecord system = systemRegistryService.create("portal", "门户", "web", null, null, null, null,
                null);
        UserRecord user = userService.create("wendy", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        assertThatThrownBy(() -> accessControlService.grant(system.getId(), List.of(user.getId()), "admin"))
                .isInstanceOf(BizException.class).hasMessageContaining("PUBLIC");
    }

    @Test
    void grantAndRevokeDriveAccessCodesAndCache() {
        SystemRegistryRecord system = systemRegistryService.create("vault", "机密系统", "secret", "PRIVATE", null,
                null, null, null);
        UserRecord user = userService.create("yara", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        String cacheKey = "modality:security:perm:TENANT_USER:" + user.getId();
        redis.opsForValue().set(cacheKey, "{}");

        accessControlService.grant(system.getId(), List.of(user.getId()), "admin");
        assertThat(accessControlService.grantedAccessCodes(user.getId()))
                .containsExactly("secret:vault:access");
        // 授权后必须即时失效该用户的权限缓存，否则最迟一个 TTL 才能看到 access 码
        assertThat(redis.opsForValue().get(cacheKey)).isNull();
        assertThat(accessControlService.query(system.getId(), null)).hasSize(1);

        accessControlService.revoke(system.getId(), user.getId());
        assertThat(accessControlService.grantedAccessCodes(user.getId())).isEmpty();
        assertThatThrownBy(() -> accessControlService.revoke(system.getId(), user.getId()))
                .isInstanceOf(BizException.class).hasMessageContaining("没有授权记录");
    }

    @Test
    void revokeBySystemClearsAll() {
        SystemRegistryRecord system = systemRegistryService.create("batch", "批处理", "ops", "PRIVATE", null, null,
                null, null);
        UserRecord first = userService.create("zack1", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        UserRecord second = userService.create("zack2", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        accessControlService.grant(system.getId(), List.of(first.getId(), second.getId()), "admin");
        assertThat(accessControlService.revokeBySystem(system.getId())).isEqualTo(2);
        assertThat(accessControlService.countBySystem(system.getId())).isZero();
    }

    @Test
    void registryDeleteBlockedByGrants() {
        SystemRegistryRecord system = systemRegistryService.create("locked", "被引用系统", "ops", "PRIVATE", null,
                null, null, null);
        UserRecord user = userService.create("amy", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        accessControlService.grant(system.getId(), List.of(user.getId()), "admin");
        assertThatThrownBy(() -> systemRegistryService.delete(system.getId()))
                .isInstanceOf(BizException.class).hasMessageContaining("授权记录");
    }

    @Test
    void disabledPrivateSystemExcludedFromCodes() {
        SystemRegistryRecord system = systemRegistryService.create("offline", "下线系统", "ops", "PRIVATE", null,
                null, null, null);
        UserRecord user = userService.create("ben", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        accessControlService.grant(system.getId(), List.of(user.getId()), "admin");
        systemRegistryService.update(system.getId(), null, null, null, null, null, "disabled");
        assertThat(accessControlService.grantedAccessCodes(user.getId())).isEmpty();
    }
}
