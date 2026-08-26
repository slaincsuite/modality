package cn.slainc.tenant.isolation;

import cn.slainc.core.exception.BizException;
import cn.slainc.datasource.definition.DataSourceDefinition;
import cn.slainc.datasource.enums.DatabaseType;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseTenantIsolationHandlerTest {

    private final DatabaseIsolationTemplate template = new DatabaseIsolationTemplate(
            "jdbc:mysql://127.0.0.1:3306/tenant_{tenantCode}", "user_{tenantId}", "pwd_{tenantId}",
            "com.mysql.cj.jdbc.Driver", "tenant");

    @AfterEach
    void clearDataSourceContext() {
        while (DynamicDataSourceContextHolder.peek() != null) {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    @DisplayName("只支持库级隔离策略，数据源名为 tenant_<租户ID>")
    void supportsOnlyDatabase() {
        DatabaseTenantIsolationHandler handler = new DatabaseTenantIsolationHandler(null, template);
        assertTrue(handler.supports(TenantIsolationStrategy.DATABASE));
        assertFalse(handler.supports(TenantIsolationStrategy.SCHEMA));
        assertEquals("tenant_5", handler.dataSourceName(5L));
        assertTrue(handler.describe().contains("tenant_"), handler.describe());
        assertThrows(BizException.class, () -> handler.dataSourceName(null));
    }

    @Test
    @DisplayName("按模板注册独立数据源并回填租户元数据")
    void registersTenantDataSource() {
        DynamicDataSourceManager manager = mock(DynamicDataSourceManager.class);
        when(manager.exists("tenant_5")).thenReturn(false);
        DatabaseTenantIsolationHandler handler = new DatabaseTenantIsolationHandler(manager, template);
        TenantInfo tenant = TenantInfo.of(5L, "acme", TenantIsolationStrategy.DATABASE);

        handler.apply(tenant);

        ArgumentCaptor<DataSourceDefinition> captor = ArgumentCaptor.forClass(DataSourceDefinition.class);
        verify(manager).register(captor.capture());
        DataSourceDefinition definition = captor.getValue();
        assertEquals("tenant_5", definition.getName());
        assertEquals("tenant", definition.getGroupName());
        assertEquals("jdbc:mysql://127.0.0.1:3306/tenant_acme", definition.getUrl());
        assertEquals("user_5", definition.getUsername());
        assertEquals("pwd_5", definition.getPassword());
        assertEquals(DatabaseType.MYSQL, definition.getDatabaseType());
        assertEquals("tenant_5", tenant.getDatasourceName());
        assertEquals("tenant_5", tenant.getMetadata().get(TenantInfo.METADATA_DATASOURCE));
    }

    @Test
    @DisplayName("数据源已存在时不重复注册")
    void skipsExistingDataSource() {
        DynamicDataSourceManager manager = mock(DynamicDataSourceManager.class);
        when(manager.exists("tenant_5")).thenReturn(true);
        DatabaseTenantIsolationHandler handler = new DatabaseTenantIsolationHandler(manager, template);
        TenantInfo tenant = TenantInfo.of(5L, "acme", TenantIsolationStrategy.DATABASE);

        handler.apply(tenant);

        verify(manager, never()).register(org.mockito.ArgumentMatchers.any());
        assertEquals("tenant_5", tenant.getDatasourceName());
    }

    @Test
    @DisplayName("缺少数据源管理器时安全跳过，缺少模板时拒绝")
    void skipsWithoutManagerAndRejectsWithoutTemplate() {
        DatabaseTenantIsolationHandler handler = new DatabaseTenantIsolationHandler(null, template);
        TenantInfo tenant = TenantInfo.of(5L, "acme", TenantIsolationStrategy.DATABASE);
        assertDoesNotThrow(() -> handler.apply(tenant));
        assertDoesNotThrow(() -> handler.remove(tenant));

        DynamicDataSourceManager manager = mock(DynamicDataSourceManager.class);
        DatabaseTenantIsolationHandler noTemplate = new DatabaseTenantIsolationHandler(manager,
                new DatabaseIsolationTemplate(null, null, null, null, null));
        BizException exception = assertThrows(BizException.class, () -> noTemplate.apply(tenant));
        assertTrue(exception.getMessage().contains("url-template"), exception.getMessage());
    }

    @Test
    @DisplayName("按租户切换数据源并在结束后恢复")
    void switchesDataSourcePerTenant() {
        DynamicDataSourceManager manager = mock(DynamicDataSourceManager.class);
        DatabaseTenantIsolationHandler handler = new DatabaseTenantIsolationHandler(manager, template);

        String inside = handler.callWith(5L, handler::current);
        assertEquals("tenant_5", inside);
        assertNull(handler.current(), "退出后应恢复原有数据源上下文");

        StringBuilder trace = new StringBuilder();
        handler.runWith(6L, () -> trace.append(handler.current()));
        assertEquals("tenant_6", trace.toString());
        assertNull(handler.current());

        assertThrows(IllegalStateException.class, () -> handler.callWith(7L, () -> {
            assertEquals("tenant_7", handler.current());
            throw new IllegalStateException("业务失败");
        }));
        assertNull(handler.current(), "异常路径同样要恢复数据源上下文");
    }

    @Test
    @DisplayName("移除租户数据源仅在存在时执行")
    void removesDataSourceWhenPresent() {
        DynamicDataSourceManager manager = mock(DynamicDataSourceManager.class);
        when(manager.exists("tenant_5")).thenReturn(true);
        DatabaseTenantIsolationHandler handler = new DatabaseTenantIsolationHandler(manager, template);
        handler.remove(TenantInfo.of(5L, "acme", TenantIsolationStrategy.DATABASE));
        verify(manager).remove("tenant_5");
    }
}
