package cn.slainc.modules.upms;

public final class UpmsKeys {

    public static final String USER_TABLE = "t_user";

    public static final String ROLE_TABLE = "t_role";

    public static final String PERMISSION_TABLE = "t_permission";

    public static final String MENU_TABLE = "t_menu";

    public static final String DEPT_TABLE = "t_dept";

    public static final String USER_GROUP_TABLE = "t_user_group";

    public static final String USER_GROUP_MEMBER_TABLE = "t_user_group_member";

    public static final String USER_ROLE_TABLE = "t_user_role";

    public static final String ROLE_PERMISSION_TABLE = "t_role_permission";

    public static final String ROLE_DATA_SCOPE_TABLE = "t_role_data_scope";

    public static final String SYSTEM_REGISTRY_TABLE = "t_system_registry";

    public static final String ACCESS_CONTROL_TABLE = "t_access_control";

    public static final String POLICY_BINDING_TABLE = "t_policy_binding";

    public static final String OPERATION_LOG_TABLE = "t_user_operation_log";

    public static final String LOGIN_RECORD_TABLE = "t_user_login_record";

    // 管理端点权限码：控制器一律引用常量，不写字面量

    public static final String PERMISSION_UPMS_ACCESS_GRANT = "upms:access:grant";
    public static final String PERMISSION_UPMS_ACCESS_QUERY = "upms:access:query";
    public static final String PERMISSION_UPMS_ACCESS_REVOKE = "upms:access:revoke";
    public static final String PERMISSION_UPMS_DEPT_CREATE = "upms:dept:create";
    public static final String PERMISSION_UPMS_DEPT_DELETE = "upms:dept:delete";
    public static final String PERMISSION_UPMS_DEPT_QUERY = "upms:dept:query";
    public static final String PERMISSION_UPMS_DEPT_UPDATE = "upms:dept:update";
    public static final String PERMISSION_UPMS_GROUP_ASSIGN = "upms:group:assign";
    public static final String PERMISSION_UPMS_GROUP_CREATE = "upms:group:create";
    public static final String PERMISSION_UPMS_GROUP_DELETE = "upms:group:delete";
    public static final String PERMISSION_UPMS_GROUP_QUERY = "upms:group:query";
    public static final String PERMISSION_UPMS_GROUP_UPDATE = "upms:group:update";
    public static final String PERMISSION_UPMS_LOG_EXPORT = "upms:log:export";
    public static final String PERMISSION_UPMS_LOG_QUERY = "upms:log:query";
    public static final String PERMISSION_UPMS_MENU_CREATE = "upms:menu:create";
    public static final String PERMISSION_UPMS_MENU_DELETE = "upms:menu:delete";
    public static final String PERMISSION_UPMS_MENU_QUERY = "upms:menu:query";
    public static final String PERMISSION_UPMS_MENU_UPDATE = "upms:menu:update";
    public static final String PERMISSION_UPMS_PERMISSION_CREATE = "upms:permission:create";
    public static final String PERMISSION_UPMS_PERMISSION_DELETE = "upms:permission:delete";
    public static final String PERMISSION_UPMS_PERMISSION_EXPORT = "upms:permission:export";
    public static final String PERMISSION_UPMS_PERMISSION_IMPORT = "upms:permission:import";
    public static final String PERMISSION_UPMS_PERMISSION_QUERY = "upms:permission:query";
    public static final String PERMISSION_UPMS_PERMISSION_UPDATE = "upms:permission:update";
    public static final String PERMISSION_UPMS_POLICY_BIND = "upms:policy:bind";
    public static final String PERMISSION_UPMS_POLICY_QUERY = "upms:policy:query";
    public static final String PERMISSION_UPMS_POLICY_UNBIND = "upms:policy:unbind";
    public static final String PERMISSION_UPMS_ROLE_ASSIGN_PERMISSION = "upms:role:assign-permission";
    public static final String PERMISSION_UPMS_ROLE_ASSIGN_USER = "upms:role:assign-user";
    public static final String PERMISSION_UPMS_ROLE_CREATE = "upms:role:create";
    public static final String PERMISSION_UPMS_ROLE_DATA_SCOPE = "upms:role:data-scope";
    public static final String PERMISSION_UPMS_ROLE_DELETE = "upms:role:delete";
    public static final String PERMISSION_UPMS_ROLE_QUERY = "upms:role:query";
    public static final String PERMISSION_UPMS_ROLE_UPDATE = "upms:role:update";
    public static final String PERMISSION_UPMS_STATS_QUERY = "upms:stats:query";
    public static final String PERMISSION_UPMS_SYSTEM_CREATE = "upms:system:create";
    public static final String PERMISSION_UPMS_SYSTEM_DELETE = "upms:system:delete";
    public static final String PERMISSION_UPMS_SYSTEM_QUERY = "upms:system:query";
    public static final String PERMISSION_UPMS_SYSTEM_UPDATE = "upms:system:update";
    public static final String PERMISSION_UPMS_SYSTEM_VISIBILITY = "upms:system:visibility";
    public static final String PERMISSION_UPMS_USER_ASSIGN_ROLE = "upms:user:assign-role";
    public static final String PERMISSION_UPMS_USER_CREATE = "upms:user:create";
    public static final String PERMISSION_UPMS_USER_DELETE = "upms:user:delete";
    public static final String PERMISSION_UPMS_USER_DISABLE = "upms:user:disable";
    public static final String PERMISSION_UPMS_USER_EXPORT = "upms:user:export";
    public static final String PERMISSION_UPMS_USER_IMPORT = "upms:user:import";
    public static final String PERMISSION_UPMS_USER_MERGE = "upms:user:merge";
    public static final String PERMISSION_UPMS_USER_QUERY = "upms:user:query";
    public static final String PERMISSION_UPMS_USER_RESET_PASSWORD = "upms:user:reset-password";
    public static final String PERMISSION_UPMS_USER_UPDATE = "upms:user:update";

    public static final String ACCESS_PERMISSION_SUFFIX = ":access";

    public static final String STATUS_ENABLED = "enabled";

    public static final String STATUS_DISABLED = "disabled";

    public static final String STATUS_LOCKED = "locked";

    private UpmsKeys() {
    }
}
