package cn.slainc.modules.tenant;

public final class TenantKeys {

    public static final String TENANT_TABLE = "t_tenant";

    public static final String PLAN_TABLE = "t_tenant_plan";

    public static final String PLAN_MENU_TABLE = "t_tenant_plan_menu";

    public static final String PERMISSION_TENANT_QUERY = "tenant:manage:query";
    public static final String PERMISSION_TENANT_CREATE = "tenant:manage:create";
    public static final String PERMISSION_TENANT_UPDATE = "tenant:manage:update";
    public static final String PERMISSION_TENANT_DELETE = "tenant:manage:delete";
    public static final String PERMISSION_TENANT_EXPORT = "tenant:manage:export";

    public static final String PERMISSION_PLAN_QUERY = "tenant:plan:query";

    public static final String PERMISSION_PLAN_CREATE = "tenant:plan:create";

    public static final String PERMISSION_PLAN_UPDATE = "tenant:plan:update";

    public static final String PERMISSION_PLAN_DELETE = "tenant:plan:delete";

    public static final String PERMISSION_PLAN_ASSIGN = "tenant:plan:assign";

    public static final String PERMISSION_PLAN_BIND = "tenant:plan:bind";

    private TenantKeys() {
    }
}
