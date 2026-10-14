package cn.slainc.modules.upms.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.access.AccessControlService;
import cn.slainc.modules.upms.service.permission.PermissionService;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.registry.SystemRegistryService;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.security.spi.PasswordAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UpmsSecurityBridgeTest extends UpmsIntegrationTestBase {

    @Autowired
    private UpmsPermissionProvider permissionProvider;

    @Autowired
    private UpmsAccountAuthProvider authProvider;

    @Autowired
    private UpmsAccountPasswordUpdater passwordUpdater;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private SystemRegistryService systemRegistryService;

    @Autowired
    private AccessControlService accessControlService;

    @Test
    void permissionsMergeRbacAndAccessCodes() {
        var read = permissionService.create("doc:read9", "读", "API", null, null, null);
        var write = permissionService.create("doc:write9", "写", "API", null, null, null);
        var role = roleService.create("editor", "编辑", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(read.getId(), write.getId()));
        UserRecord user = userService.create("bridge", null, "Bridge#123", null, null, null, LoginType.TENANT_USER,
                1L, "admin");
        userService.assignRoles(user.getId(), List.of(role.getId()));
        systemRegistryService.create("intranet", "内网", "web", "PUBLIC", null, null, null, null);
        var vault = systemRegistryService.create("safe", "保险箱", "secret", "PRIVATE", null, null, null, null);
        accessControlService.grant(vault.getId(), List.of(user.getId()), "admin");

        assertThat(permissionProvider.getPermissions(user.getId(), LoginType.TENANT_USER))
                .containsExactlyInAnyOrder("doc:read9", "doc:write9", "web:intranet:access", "secret:safe:access");
        assertThat(permissionProvider.getRoles(user.getId(), LoginType.TENANT_USER)).containsExactly("editor");
    }

    @Test
    void nonHumanLoginTypesIgnored() {
        UserRecord user = userService.create("appuser", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        assertThat(permissionProvider.getPermissions(user.getId(), LoginType.APP)).isEmpty();
        assertThat(permissionProvider.getRoles(user.getId(), LoginType.SERVICE)).isEmpty();
        assertThat(permissionProvider.getPermissions("not-a-number", LoginType.TENANT_USER)).isEmpty();
    }

    @Test
    void disabledUserLosesPermissionsImmediately() {
        UserRecord user = userService.create("ghost", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        var role = roleService.create("temp", "临时", null, null, null, null, null);
        var perm = permissionService.create("doc:temp", "临时权限", "API", null, null, null);
        roleService.assignPermissions(role.getId(), List.of(perm.getId()));
        userService.assignRoles(user.getId(), List.of(role.getId()));
        assertThat(permissionProvider.getPermissions(user.getId(), LoginType.TENANT_USER))
                .containsExactly("doc:temp");
        userService.changeStatus(user.getId(), UpmsKeys.STATUS_DISABLED);
        assertThat(permissionProvider.getPermissions(user.getId(), LoginType.TENANT_USER)).isEmpty();
    }

    @Test
    void authProviderReadsUserTable() {
        UserRecord user = userService.create("login-user", "登录用户", "Plain#12345", null, null, null,
                LoginType.TENANT_ADMIN, 1L, "admin");
        PasswordAccount account = authProvider.findAccount("login-user", LoginType.TENANT_ADMIN);
        assertThat(account).isNotNull();
        assertThat(account.userId()).isEqualTo(user.getId());
        assertThat(account.enabled()).isTrue();
        assertThat(account.mustChangePassword()).isFalse();
        assertThat(authProvider.findAccount("login-user", LoginType.TENANT_USER)).isNull();
        assertThat(authProvider.findAccountByUserId(user.getId(), LoginType.TENANT_ADMIN)).isNotNull();
        assertThat(authProvider.findAccount("anyone", LoginType.GUEST)).isNull();
    }

    @Test
    void passwordUpdaterWritesHashAndClearsFlag() {
        UserRecord user = userService.create("changepw", null, "First#12345", null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        String newHash = cn.slainc.crypto.hash.BCryptUtils.hash("Second#12345");
        passwordUpdater.updatePassword(user.getId(), newHash, LoginType.TENANT_USER);
        PasswordAccount account = authProvider.findAccount("changepw", LoginType.TENANT_USER);
        assertThat(account.passwordHash()).isEqualTo(newHash);
        assertThat(account.mustChangePassword()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        passwordUpdater.updatePassword(user.getId(), "plaintext", LoginType.TENANT_USER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
