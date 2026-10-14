package cn.slainc.modules.tenant;

public final class TenantKeys {

    public static final String TENANT_TABLE = "t_tenant";

    public static final String PERMISSION_TENANT_QUERY = "tenant:manage:query";
    public static final String PERMISSION_TENANT_CREATE = "tenant:manage:create";
    public static final String PERMISSION_TENANT_UPDATE = "tenant:manage:update";
    public static final String PERMISSION_TENANT_DELETE = "tenant:manage:delete";

    private TenantKeys() {
    }
}
