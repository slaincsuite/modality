package cn.slainc.modules.upms.web;

import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.web.dept.DeptManageController;
import cn.slainc.modules.upms.web.group.UserGroupManageController;
import cn.slainc.modules.upms.web.menu.MenuManageController;
import cn.slainc.modules.upms.web.permission.PermissionManageController;
import cn.slainc.modules.upms.web.role.RoleManageController;
import cn.slainc.modules.upms.web.self.UserPreferencesController;
import cn.slainc.modules.upms.web.self.SelfProfileController;
import cn.slainc.modules.upms.web.settings.UserSettingsController;
import cn.slainc.modules.upms.web.user.TenantUserController;
import cn.slainc.modules.upms.web.user.UserManageController;
import cn.slainc.modules.upms.web.user.UserStatsController;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.security.annotation.RequireLogin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UpmsEndpointContractTest {

    private static final Map<String, String> MANAGEMENT_ENDPOINTS = Map.ofEntries(
            Map.entry("RoleManageController#page", UpmsKeys.PERMISSION_UPMS_ROLE_QUERY),
            Map.entry("RoleManageController#get", UpmsKeys.PERMISSION_UPMS_ROLE_QUERY),
            Map.entry("RoleManageController#dataScope", UpmsKeys.PERMISSION_UPMS_ROLE_QUERY),
            Map.entry("RoleManageController#userBindings", UpmsKeys.PERMISSION_UPMS_ROLE_QUERY),
            Map.entry("RoleManageController#permissionSources", UpmsKeys.PERMISSION_UPMS_ROLE_QUERY),
            Map.entry("RoleManageController#groups", UpmsKeys.PERMISSION_UPMS_ROLE_QUERY),
            Map.entry("RoleManageController#assignGroups", UpmsKeys.PERMISSION_UPMS_ROLE_ASSIGN_USER),
            Map.entry("UserGroupManageController#memberBindings", UpmsKeys.PERMISSION_UPMS_GROUP_QUERY),
            Map.entry("UserGroupManageController#roles", UpmsKeys.PERMISSION_UPMS_GROUP_QUERY),
            Map.entry("UserGroupManageController#assignRoles", UpmsKeys.PERMISSION_UPMS_GROUP_ASSIGN),
            Map.entry("DeptManageController#get", UpmsKeys.PERMISSION_UPMS_DEPT_QUERY),
            Map.entry("DeptManageController#roles", UpmsKeys.PERMISSION_UPMS_DEPT_QUERY),
            Map.entry("MenuManageController#list", UpmsKeys.PERMISSION_UPMS_MENU_QUERY),
            Map.entry("MenuManageController#roles", UpmsKeys.PERMISSION_UPMS_MENU_QUERY),
            Map.entry("PermissionManageController#page", UpmsKeys.PERMISSION_UPMS_PERMISSION_QUERY),
            Map.entry("PermissionManageController#get", UpmsKeys.PERMISSION_UPMS_PERMISSION_QUERY),
            Map.entry("PermissionManageController#roles", UpmsKeys.PERMISSION_UPMS_PERMISSION_QUERY),
            Map.entry("PermissionManageController#menus", UpmsKeys.PERMISSION_UPMS_PERMISSION_QUERY),
            Map.entry("UserManageController#page", UpmsKeys.PERMISSION_UPMS_USER_QUERY),
            Map.entry("UserManageController#groups", UpmsKeys.PERMISSION_UPMS_USER_QUERY),
            Map.entry("UserManageController#permissionBoundary", UpmsKeys.PERMISSION_UPMS_USER_QUERY),
            Map.entry("UserManageController#updatePermissionBoundary",
                    UpmsKeys.PERMISSION_UPMS_USER_PERMISSION_BOUNDARY),
            Map.entry("UserManageController#updateDept", UpmsKeys.PERMISSION_UPMS_USER_UPDATE),
            Map.entry("UserManageController#changeConsoleAccess", UpmsKeys.PERMISSION_UPMS_USER_SECURITY),
            Map.entry("UserManageController#changeMustChangePassword", UpmsKeys.PERMISSION_UPMS_USER_SECURITY),
            Map.entry("UserSettingsController#get", UpmsKeys.PERMISSION_UPMS_SETTINGS_QUERY),
            Map.entry("UserSettingsController#update", UpmsKeys.PERMISSION_UPMS_SETTINGS_UPDATE),
            Map.entry("UserSettingsController#history", UpmsKeys.PERMISSION_UPMS_SETTINGS_QUERY),
            Map.entry("TenantUserController#users", UpmsKeys.PERMISSION_UPMS_USER_QUERY),
            Map.entry("TenantUserController#export", UpmsKeys.PERMISSION_UPMS_USER_EXPORT),
            Map.entry("UserStatsController#activeUsers", UpmsKeys.PERMISSION_UPMS_STATS_QUERY));

    @Test
    void managementEndpointsDeclareExpectedPermissions() {
        assertThat(MANAGEMENT_ENDPOINTS).hasSize(31);
        MANAGEMENT_ENDPOINTS.forEach((key, permission) -> {
            Method method = endpoint(key);
            RequirePermission annotation = method.getAnnotation(RequirePermission.class);
            assertThat(annotation).as(key + " 缺少权限码").isNotNull();
            assertThat(annotation.permissions()).as(key + " 权限码不一致").containsExactly(permission);
        });
    }

    @Test
    void newWriteEndpointsDeclareAuditAndSelfPreferenceStaysLoginOnly() {
        for (String key : new String[]{"RoleManageController#assignGroups", "UserGroupManageController#assignRoles",
                "UserManageController#updateDept", "UserManageController#changeConsoleAccess",
                "UserManageController#changeMustChangePassword", "UserManageController#updatePermissionBoundary",
                "UserSettingsController#update", "UserPreferencesController#update",
                "TenantUserController#export"}) {
            assertThat(endpoint(key).getAnnotation(AuditLog.class)).as(key + " 缺少审计").isNotNull();
        }
        Method preferences = endpoint("UserPreferencesController#update");
        assertThat(preferences.getAnnotation(RequirePermission.class)).isNull();

        for (String name : new String[]{"get", "update", "updateAvatar"}) {
            Method method = endpoint("SelfProfileController#" + name);
            assertThat(method.getAnnotation(RequireLogin.class)).as(name + " 应仅要求登录").isNotNull();
            assertThat(method.getAnnotation(RequirePermission.class)).as(name + " 不应要求管理权限").isNull();
        }
        assertThat(endpoint("SelfProfileController#get").getReturnType().getSimpleName())
                .isEqualTo("SelfProfileView");
        assertThat(endpoint("SelfProfileController#update").getReturnType().getSimpleName())
                .isEqualTo("SelfProfileView");
        assertThat(endpoint("SelfProfileController#update").getAnnotation(AuditLog.class)).isNotNull();
        assertThat(endpoint("SelfProfileController#updateAvatar").getAnnotation(AuditLog.class)).isNotNull();
    }

    private Method endpoint(String key) {
        String[] parts = key.split("#");
        Class<?> type = switch (parts[0]) {
            case "RoleManageController" -> RoleManageController.class;
            case "UserGroupManageController" -> UserGroupManageController.class;
            case "DeptManageController" -> DeptManageController.class;
            case "MenuManageController" -> MenuManageController.class;
            case "PermissionManageController" -> PermissionManageController.class;
            case "UserManageController" -> UserManageController.class;
            case "UserSettingsController" -> UserSettingsController.class;
            case "UserPreferencesController" -> UserPreferencesController.class;
            case "SelfProfileController" -> SelfProfileController.class;
            case "TenantUserController" -> TenantUserController.class;
            case "UserStatsController" -> UserStatsController.class;
            default -> throw new IllegalArgumentException("未知控制器: " + parts[0]);
        };
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(parts[1])) {
                return method;
            }
        }
        throw new IllegalArgumentException("未知端点: " + key);
    }
}
