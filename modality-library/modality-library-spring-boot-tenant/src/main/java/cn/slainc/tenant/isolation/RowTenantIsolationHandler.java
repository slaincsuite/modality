package cn.slainc.tenant.isolation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RowTenantIsolationHandler implements TenantIsolationHandler {

    private static final Logger log = LoggerFactory.getLogger(RowTenantIsolationHandler.class);

    @Override
    public boolean supports(TenantIsolationStrategy strategy) {
        return strategy == TenantIsolationStrategy.ROW;
    }

    @Override
    public void apply(TenantInfo tenant) {
        log.debug("租户 [{}] 采用行级隔离，无需创建隔离资源", tenant.getTenantId());
    }

    @Override
    public void remove(TenantInfo tenant) {
        log.debug("租户 [{}] 采用行级隔离，无隔离资源需要清理", tenant.getTenantId());
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public String describe() {
        return "行级隔离：共享库表，由 MyBatis 租户拦截器按 tenant_id 过滤数据，无需额外初始化";
    }
}
