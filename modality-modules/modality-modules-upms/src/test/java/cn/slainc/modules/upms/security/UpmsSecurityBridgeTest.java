package cn.slainc.modules.upms.security;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
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
    private UpmsAccountRestrictionPort restrictionPort;

    @Autowired
    private UserMapper userMapper;

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
    void authProviderAcceptsPhoneInsideLoginType() {
        String phone = "13800001111";
        UserRecord user = userService.create("phone-login", null, "Plain#12345", null, phone, null,
                LoginType.TENANT_USER, 1L, "admin");

        PasswordAccount byPhone = authProvider.findAccount(phone, LoginType.TENANT_USER);
        assertThat(byPhone).isNotNull();
        assertThat(byPhone.userId()).isEqualTo(user.getId());
        assertThat(byPhone.username()).isEqualTo("phone-login");
        assertThat(authProvider.findAccount(phone, LoginType.TENANT_ADMIN)).isNull();
    }

    @Test
    void phoneLoginIsScopedToSelectedTenant() {
        String phone = "13800002222";
        UserRecord tenantOne = userService.create("tenant-one-phone", null, "Plain#12345", null, phone, null,
                LoginType.TENANT_USER, 1L, "admin");
        UserRecord tenantTwo = TenantContext.callWith(2L, () -> userService.create("tenant-two-phone", null,
                "Plain#12345", null, phone, null, LoginType.TENANT_USER, 2L, "admin"));

        PasswordAccount selectedOne = authProvider.findAccount(phone, LoginType.TENANT_USER);
        assertThat(selectedOne).isNotNull();
        assertThat(selectedOne.userId()).isEqualTo(tenantOne.getId());

        TenantContext.runWith(2L, () -> {
            PasswordAccount selectedTwo = authProvider.findAccount(phone, LoginType.TENANT_USER);
            assertThat(selectedTwo).isNotNull();
            assertThat(selectedTwo.userId()).isEqualTo(tenantTwo.getId());
        });
        TenantContext.runWith(3L,
                () -> assertThat(authProvider.findAccount(phone, LoginType.TENANT_USER)).isNull());
    }

    @Test
    void duplicatePhoneFailsClosed() {
        String phone = "13800003333";
        userService.create("duplicate-phone-a", null, "Plain#12345", null, phone, null,
                LoginType.TENANT_USER, 1L, "admin");
        userService.create("duplicate-phone-b", null, "Plain#12345", null, phone, null,
                LoginType.TENANT_USER, 1L, "admin");

        assertThat(authProvider.findAccount(phone, LoginType.TENANT_USER)).isNull();
    }

    @Test
    void phoneLoginAppliesAccountRestrictions() {
        String phone = "13800004444";
        UserRecord user = userService.create("restricted-phone", null, "Plain#12345", null, phone, null,
                LoginType.TENANT_USER, 1L, "admin");
        user.setAllowedIps("10.0.0.*");
        user.setAllowedDevices("PC");
        userMapper.updateById(user);

        assertThat(restrictionPort.validate(phone, LoginType.TENANT_USER, "10.0.0.8", "PC")).isEmpty();
        assertThat(restrictionPort.validate(phone, LoginType.TENANT_USER, "172.16.0.8", "MOBILE"))
                .containsExactly("当前 IP 不在账号允许的 IP 范围内", "当前设备类型未被账号允许");
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
