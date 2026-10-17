package cn.slainc.modules.upms.service.user;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.dto.PermissionBoundaryRequest;
import cn.slainc.modules.upms.dto.PermissionBoundaryView;
import cn.slainc.modules.upms.dto.UserPreferencesView;
import cn.slainc.modules.upms.dto.UserSettingsRequest;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.entity.UserSettingsRecord;
import cn.slainc.modules.upms.security.UpmsPermissionProvider;
import cn.slainc.modules.upms.service.permission.PermissionService;
import cn.slainc.modules.upms.service.preference.UserPreferencesService;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.settings.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSettingsPreferenceBoundaryTest extends UpmsIntegrationTestBase {

    @Autowired
    private UserSettingsService settingsService;

    @Autowired
    private UserPreferencesService preferencesService;

    @Autowired
    private UserPermissionBoundaryService boundaryService;

    @Autowired
    private UpmsPermissionProvider permissionProvider;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserService userService;

    @Test
    void settingsUpdateCoversProductFieldsAndWritesHistory() {
        RoleRecord role = roleService.create("default-role", "默认角色", null, null, null, null, null);
        UserSettingsRecord defaults = settingsService.current();
        assertThat(defaults.getPasswordMinLength()).isEqualTo(8);
        assertThat(defaults.getDefaultLoginType()).isEqualTo(LoginType.TENANT_USER.value());

        UserSettingsRecord updated = settingsService.update(new UserSettingsRequest(10, 4, 30, true,
                LoginType.TENANT_ADMIN.value(), role.getId(), false, false, 7, 20, 15, 60, true, 45), "tester");
        assertThat(updated.getPasswordMinLength()).isEqualTo(10);
        assertThat(updated.getDefaultRoleId()).isEqualTo(role.getId());
        assertThat(updated.getRestrictIdleUsers()).isTrue();
        assertThat(settingsService.history("password", 10)).hasSize(1);
        assertThat(settingsService.history(null, 200)).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void settingsRejectInvalidSessionRanges() {
        assertThatThrownBy(() -> settingsService.update(new UserSettingsRequest(null, null, null, null, null, null,
                null, null, null, null, 90, 60, null, null), "tester"))
                .isInstanceOf(BizException.class).hasMessageContaining("不能大于最大登录保持时间");
    }

    @Test
    void preferencesAreIsolatedByCurrentUserAndScene() {
        UserContext.set(UserContext.UserInfo.of(101L, "pref-a", LoginType.TENANT_USER));
        preferencesService.update("user-list", Map.of("pageSize", 20, "columns", List.of("name", "status")));
        UserPreferencesView first = preferencesService.get("user-list");
        assertThat(first.settings()).containsEntry("pageSize", 20);

        UserContext.set(UserContext.UserInfo.of(102L, "pref-b", LoginType.TENANT_USER));
        assertThat(preferencesService.get("user-list").settings()).isEmpty();
        preferencesService.update("user-list", Map.of("pageSize", 50));

        UserContext.set(UserContext.UserInfo.of(101L, "pref-a", LoginType.TENANT_USER));
        assertThat(preferencesService.get("user-list").settings()).containsEntry("pageSize", 20);
        assertThatThrownBy(() -> preferencesService.get("../escape"))
                .isInstanceOf(BizException.class).hasMessageContaining("偏好场景");
    }

    @Test
    void permissionBoundaryRejectsConflictsAndDenyOverridesGrantedPermission() {
        PermissionRecord read = permissionService.create("boundary:read", "边界读", "API", null, null, null);
        PermissionRecord write = permissionService.create("boundary:write", "边界写", "API", null, null, null);
        UserRecord user = userService.create("boundary-user", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        RoleRecord role = roleService.create("boundary-role", "边界角色", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(read.getId(), write.getId()));
        userService.assignRoles(user.getId(), List.of(role.getId()));

        PermissionBoundaryView boundary = boundaryService.update(user.getId(),
                new PermissionBoundaryRequest(List.of("boundary:read"), List.of("boundary:write")));
        assertThat(boundary.allowedPermissions()).containsExactly("boundary:read");
        assertThat(boundaryService.allows(user.getId(), "boundary:write")).isFalse();
        assertThat(permissionProvider.getPermissions(user.getId(), LoginType.TENANT_USER))
                .contains("boundary:read").doesNotContain("boundary:write");

        assertThatThrownBy(() -> boundaryService.update(user.getId(),
                new PermissionBoundaryRequest(List.of("boundary:read"), List.of("boundary:read"))))
                .isInstanceOf(BizException.class).hasMessageContaining("不能同时出现");
        assertThatThrownBy(() -> boundaryService.update(user.getId(),
                new PermissionBoundaryRequest(List.of("boundary:missing"), List.of())))
                .isInstanceOf(BizException.class).hasMessageContaining("不存在");
    }
}
