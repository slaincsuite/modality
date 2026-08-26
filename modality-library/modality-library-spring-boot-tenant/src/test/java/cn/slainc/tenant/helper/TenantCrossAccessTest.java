package cn.slainc.tenant.helper;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantCrossAccessTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        UserContext.clear();
    }

    @Test
    @DisplayName("指定租户查询会临时切换并在结束后恢复")
    void queryAsSwitchesAndRestores() {
        TenantContext.set(1L);
        String result = TenantCrossAccess.queryAs(2L, () -> "租户" + TenantContext.get());
        assertEquals("租户2", result);
        assertEquals(1L, TenantContext.get());

        TenantContext.clear();
        TenantCrossAccess.runAs(3L, () -> assertEquals(3L, TenantContext.get()));
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("跨租户统计仅限超级管理员")
    void crossTenantStatisticsRequireSuperAdmin() {
        BizException exception = assertThrows(BizException.class,
                () -> TenantCrossAccess.forEachTenant(List.of(1L, 2L), tenantId -> TenantContext.get()));
        assertTrue(exception.getMessage().contains("跨租户统计仅限超级管理员"), exception.getMessage());
        assertEquals(ResultCode.FORBIDDEN.code(), exception.errorCode().code());
    }

    @Test
    @DisplayName("超级管理员可逐租户取值，空值与重复租户被过滤")
    void superAdminCollectsPerTenant() {
        UserContext.set(superAdmin());
        Map<Long, Long> values = TenantCrossAccess.forEachTenant(Arrays.asList(2L, null, 1L, 2L),
                tenantId -> TenantContext.get());
        assertEquals(List.of(2L, 1L), List.copyOf(values.keySet()));
        assertEquals(2L, values.get(2L));
        assertEquals(1L, values.get(1L));
        assertNull(TenantContext.get(), "统计结束后不应残留租户上下文");
    }

    @Test
    @DisplayName("按租户汇总金额并计算总和")
    void sumsByTenant() {
        UserContext.set(superAdmin());
        Map<Long, BigDecimal> sums = TenantCrossAccess.sumByTenant(List.of(1L, 2L, 3L),
                tenantId -> tenantId == 3L ? null : BigDecimal.valueOf(tenantId * 100L));
        assertEquals(BigDecimal.valueOf(100L), sums.get(1L));
        assertEquals(BigDecimal.valueOf(200L), sums.get(2L));
        assertEquals(BigDecimal.ZERO, sums.get(3L));
        assertEquals(BigDecimal.valueOf(300L), TenantCrossAccess.total(List.of(1L, 2L, 3L),
                tenantId -> tenantId == 3L ? null : BigDecimal.valueOf(tenantId * 100L)));
    }

    private static UserContext.UserInfo superAdmin() {
        return new UserContext.UserInfo(1L, "admin", null, Set.of("admin"), Set.of("*"), true);
    }
}
