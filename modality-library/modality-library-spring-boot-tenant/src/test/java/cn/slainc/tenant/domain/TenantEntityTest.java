package cn.slainc.tenant.domain;

import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.isolation.TenantIsolationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantEntityTest {

    @Test
    @DisplayName("租户实体的默认状态与默认隔离策略")
    void entityDefaults() {
        Tenant tenant = new Tenant();
        assertEquals(TenantStatus.TRIAL, tenant.getStatus());
        assertEquals(TenantIsolationStrategy.ROW, tenant.getIsolationStrategy());
        assertNull(tenant.getTenantCode());
        tenant.setTenantCode("acme");
        assertEquals("acme", tenant.getTenantCode());
    }

    @Test
    @DisplayName("过期与可用性判定基于过期时间")
    void expireAndUsableCheck() {
        Tenant tenant = new Tenant();
        LocalDateTime now = LocalDateTime.now();
        assertFalse(tenant.isExpired(now), "未设置过期时间表示长期有效");
        assertTrue(tenant.isUsable(now));

        tenant.setExpireAt(now.minusDays(1L));
        assertTrue(tenant.isExpired(now));
        assertFalse(tenant.isUsable(now));

        tenant.setExpireAt(now.plusDays(1L));
        assertFalse(tenant.isExpired(now));
        tenant.setStatus(TenantStatus.FROZEN);
        assertFalse(tenant.isUsable(now));
        tenant.setStatus(TenantStatus.DISABLED);
        assertFalse(tenant.isUsable(now));
        tenant.setStatus(TenantStatus.ACTIVE);
        assertTrue(tenant.isUsable(now));
        assertFalse(tenant.isExpired(null));
    }

    @Test
    @DisplayName("租户状态带中文说明并支持按名称解析")
    void statusHasChineseDisplayName() {
        assertEquals("试用", TenantStatus.TRIAL.getDisplayName());
        assertEquals("正常", TenantStatus.ACTIVE.getDisplayName());
        assertEquals("过期", TenantStatus.EXPIRED.getDisplayName());
        assertEquals("冻结", TenantStatus.FROZEN.getDisplayName());
        assertEquals("停用", TenantStatus.DISABLED.getDisplayName());
        assertTrue(TenantStatus.TRIAL.isUsable());
        assertTrue(TenantStatus.ACTIVE.isUsable());
        assertFalse(TenantStatus.EXPIRED.isUsable());
        assertEquals(TenantStatus.FROZEN, TenantStatus.of("frozen", null));
        assertEquals(TenantStatus.ACTIVE, TenantStatus.of("正常", null));
        assertEquals(TenantStatus.TRIAL, TenantStatus.of("unknown", TenantStatus.TRIAL));
        assertEquals(TenantStatus.TRIAL, TenantStatus.of(null, TenantStatus.TRIAL));
    }
}
