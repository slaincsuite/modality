package cn.slainc.tenant.lifecycle;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.tenant.domain.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTenantInitializerTest {

    @Test
    @DisplayName("未配置初始化脚本时不执行任何 SQL")
    void skipsWithoutStatements() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DefaultTenantInitializer initializer = new DefaultTenantInitializer(new TenantDataTemplate(), jdbcTemplate,
                null);
        initializer.initialize(tenant());
        verify(jdbcTemplate, never()).execute(anyString());
        assertEquals(100, initializer.order());
    }

    @Test
    @DisplayName("按模板执行初始化脚本并替换占位符")
    void executesRenderedStatements() {
        TenantDataTemplate template = new TenantDataTemplate();
        template.setRoleCodes(List.of("admin", "user"));
        template.setInitStatements(List.of(
                "INSERT INTO t_role (tenant_id, code) VALUES ({tenantId}, 'admin')",
                "INSERT INTO t_dict (tenant_code, name) VALUES ('{tenantCode}', '{tenantName}')"));
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DefaultTenantInitializer initializer = new DefaultTenantInitializer(template, jdbcTemplate, null);

        initializer.initialize(tenant());

        verify(jdbcTemplate).execute("INSERT INTO t_role (tenant_id, code) VALUES (7, 'admin')");
        verify(jdbcTemplate).execute("INSERT INTO t_dict (tenant_code, name) VALUES ('acme', 'Acme 公司')");
    }

    @Test
    @DisplayName("配置了脚本但没有数据库时安全跳过")
    void skipsSafelyWithoutJdbcTemplate() {
        TenantDataTemplate template = new TenantDataTemplate();
        template.setInitStatements(List.of("INSERT INTO t_role (tenant_id) VALUES ({tenantId})"));
        assertDoesNotThrow(() -> new DefaultTenantInitializer(template, null, null).initialize(tenant()));
    }

    @Test
    @DisplayName("动态配置可覆盖脚本与默认数据")
    void dynamicConfigOverridesTemplate() {
        DynamicConfigService configService = mock(DynamicConfigService.class);
        when(configService.getList(DefaultTenantInitializer.STATEMENTS_KEY, String.class))
                .thenReturn(List.of("INSERT INTO t_menu (tenant_id) VALUES ({tenantId})"));
        when(configService.getList(DefaultTenantInitializer.ROLES_KEY, String.class)).thenReturn(List.of("ops"));
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TenantDataTemplate template = new TenantDataTemplate();
        template.setInitStatements(List.of("INSERT INTO t_role (tenant_id) VALUES ({tenantId})"));
        DefaultTenantInitializer initializer = new DefaultTenantInitializer(template, jdbcTemplate, configService);

        initializer.initialize(tenant());

        verify(jdbcTemplate).execute("INSERT INTO t_menu (tenant_id) VALUES (7)");
        verify(jdbcTemplate, never()).execute("INSERT INTO t_role (tenant_id) VALUES (7)");
    }

    @Test
    @DisplayName("模板为空判断与拷贝构造正确")
    void templateCopyAndEmptyCheck() {
        TenantDataTemplate template = new TenantDataTemplate();
        assertTrue(template.isEmpty());
        template.setMenus(List.of("dashboard"));
        TenantDataTemplate copy = new TenantDataTemplate(template);
        assertEquals(List.of("dashboard"), copy.getMenus());
        copy.getMenus().add("report");
        assertEquals(1, template.getMenus().size(), "拷贝构造不应共享集合");
        template.setRoleCodes(null);
        assertTrue(template.getRoleCodes().isEmpty());
        assertTrue(new TenantDataTemplate(null).isEmpty());
    }

    private static Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(7L);
        tenant.setTenantCode("acme");
        tenant.setTenantName("Acme 公司");
        return tenant;
    }
}
