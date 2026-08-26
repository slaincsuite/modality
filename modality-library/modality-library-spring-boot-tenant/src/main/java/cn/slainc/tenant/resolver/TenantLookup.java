package cn.slainc.tenant.resolver;

@FunctionalInterface
public interface TenantLookup {

    Long findTenantIdByCode(String tenantCode);
}
