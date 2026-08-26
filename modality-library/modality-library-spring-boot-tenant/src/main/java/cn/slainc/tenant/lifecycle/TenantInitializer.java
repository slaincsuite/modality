package cn.slainc.tenant.lifecycle;

import cn.slainc.tenant.entity.Tenant;

public interface TenantInitializer {

    void initialize(Tenant tenant);

    default int order() {
        return 100;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
