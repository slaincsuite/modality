package cn.slainc.tenant.isolation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowTenantIsolationHandlerTest {

    private final RowTenantIsolationHandler handler = new RowTenantIsolationHandler();

    @Test
    @DisplayName("只支持行级隔离策略")
    void supportsOnlyRow() {
        assertTrue(handler.supports(TenantIsolationStrategy.ROW));
        assertFalse(handler.supports(TenantIsolationStrategy.SCHEMA));
        assertFalse(handler.supports(TenantIsolationStrategy.DATABASE));
    }

    @Test
    @DisplayName("行级隔离不创建也不清理资源，但给出中文说明")
    void applyAndRemoveAreNoOps() {
        TenantInfo tenant = TenantInfo.of(1L, "acme", TenantIsolationStrategy.ROW);
        assertDoesNotThrow(() -> handler.apply(tenant));
        assertDoesNotThrow(() -> handler.remove(tenant));
        assertTrue(handler.describe().contains("MyBatis 租户拦截器"), handler.describe());
        assertTrue(handler.describe().contains("tenant_id"));
    }
}
