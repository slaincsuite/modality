package cn.slainc.tenant.isolation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantIsolationStrategyTest {

    @Test
    @DisplayName("三种隔离策略都带中文描述")
    void strategiesHaveChineseDescriptions() {
        assertEquals("行级隔离", TenantIsolationStrategy.ROW.getDisplayName());
        assertEquals("Schema 隔离", TenantIsolationStrategy.SCHEMA.getDisplayName());
        assertEquals("库级隔离", TenantIsolationStrategy.DATABASE.getDisplayName());
        assertEquals("共享库表，通过 tenant_id 条件隔离数据", TenantIsolationStrategy.ROW.getDescription());
        assertEquals(3, TenantIsolationStrategy.values().length);
    }

    @Test
    @DisplayName("按枚举名或中文名解析，大小写不敏感")
    void resolvesByEnumNameOrDisplayName() {
        assertEquals(TenantIsolationStrategy.SCHEMA, TenantIsolationStrategy.of("schema"));
        assertEquals(TenantIsolationStrategy.DATABASE, TenantIsolationStrategy.of(" Database "));
        assertEquals(TenantIsolationStrategy.ROW, TenantIsolationStrategy.of("行级隔离"));
        assertNull(TenantIsolationStrategy.of("unknown"));
        assertNull(TenantIsolationStrategy.of("  "));
        assertNull(TenantIsolationStrategy.of(null));
    }

    @Test
    @DisplayName("解析失败时使用默认值")
    void fallsBackToDefault() {
        assertEquals(TenantIsolationStrategy.ROW, TenantIsolationStrategy.of("unknown", TenantIsolationStrategy.ROW));
        assertEquals(TenantIsolationStrategy.DATABASE,
                TenantIsolationStrategy.of(null, TenantIsolationStrategy.DATABASE));
    }
}
