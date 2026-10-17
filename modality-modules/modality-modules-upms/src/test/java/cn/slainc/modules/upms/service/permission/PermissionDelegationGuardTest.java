package cn.slainc.modules.upms.service.permission;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionDelegationGuardTest extends UpmsIntegrationTestBase {

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserService userService;

    @Autowired
    private PermissionService permissionService;

    @Test
    void cannotGrantPermissionHeldByNoOneButAnotherRole() {
        PermissionRecord strong = permissionService.create("tenant:manage:delete", "注销租户", "API",
                null, null, null);
        RoleRecord role = roleService.create("limited-admin", "受限管理员", null, null, null, null, null);
        UserContext.set(new UserContext.UserInfo(900L, "limited", LoginType.TENANT_USER,
                Set.of(), Set.of("upms:role:assign-permission"), false));

        assertThatThrownBy(() -> roleService.assignPermissions(role.getId(), List.of(strong.getId())))
                .isInstanceOf(BizException.class).hasMessageContaining("当前账号未持有")
                .hasMessageContaining("tenant:manage:delete");

        UserContext.set(new UserContext.UserInfo(900L, "limited", LoginType.TENANT_USER,
                Set.of(), Set.of("tenant:manage:delete"), false));
        roleService.assignPermissions(role.getId(), List.of(strong.getId()));
    }

    @Test
    void cannotAssignRoleThatContainsPermissionsTheActorDoesNotHold() {
        PermissionRecord strong = permissionService.create("upms:system:update", "维护系统", "API",
                null, null, null);
        RoleRecord role = roleService.create("system-role", "系统角色", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(strong.getId()));
        UserRecord user = userService.create("delegated-user", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "tester");

        UserContext.set(new UserContext.UserInfo(901L, "delegator", LoginType.TENANT_USER,
                Set.of(), Set.of("upms:user:assign-role"), false));
        assertThatThrownBy(() -> userService.assignRoles(user.getId(), List.of(role.getId())))
                .isInstanceOf(BizException.class).hasMessageContaining("当前账号未持有")
                .hasMessageContaining("upms:system:update");

        UserContext.set(new UserContext.UserInfo(901L, "delegator", LoginType.TENANT_USER,
                Set.of(), Set.of("*"), false));
        userService.assignRoles(user.getId(), List.of(role.getId()));
    }
}
