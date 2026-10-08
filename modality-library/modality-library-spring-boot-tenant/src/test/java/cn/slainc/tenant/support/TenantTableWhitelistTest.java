package cn.slainc.tenant.support;

import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantTableWhitelistTest {

    @Test
    @DisplayName("租户模块白名单合并进 MyBatis 配置且不重复")
    void mergesIntoMybatisProperties() {
        MybatisProperties mybatisProperties = new MybatisProperties();
        int defaultTables = mybatisProperties.getTenant().getIgnoreTables().size();
        int defaultPrefixes = mybatisProperties.getTenant().getIgnoreTablePrefixes().size();
        TenantTableWhitelist whitelist = new TenantTableWhitelist(
                List.of("t_tenant_extra", "t_tenant", "  "),
                List.of("cfg_", "act_"), mybatisProperties);
        whitelist.afterPropertiesSet();

        assertEquals(defaultTables + 1, mybatisProperties.getTenant().getIgnoreTables().size());
        assertEquals(defaultPrefixes + 1, mybatisProperties.getTenant().getIgnoreTablePrefixes().size());
        assertTrue(mybatisProperties.getTenant().getIgnoreTables().contains("t_tenant_extra"));
        assertTrue(mybatisProperties.getTenant().getIgnoreTablePrefixes().contains("cfg_"));
        assertEquals(List.of("t_tenant_extra", "t_tenant"), whitelist.tables());
        assertEquals(List.of("cfg_", "act_"), whitelist.prefixes());
    }

    @Test
    @DisplayName("白名单判定忽略大小写、库名前缀与引号")
    void isIgnoredNormalizesTableName() {
        TenantTableWhitelist whitelist = new TenantTableWhitelist(List.of("t_tenant"), List.of("cfg_"),
                new MybatisProperties());
        assertTrue(whitelist.isIgnored("t_tenant"));
        assertTrue(whitelist.isIgnored("T_TENANT"));
        assertTrue(whitelist.isIgnored("public.t_tenant"));
        assertTrue(whitelist.isIgnored("cfg_setting"));
        assertTrue(whitelist.isIgnored("CFG_SETTING"));
        assertFalse(whitelist.isIgnored("t_user"));
        assertFalse(whitelist.isIgnored(null));
        assertFalse(whitelist.isIgnored("   "));
        assertEquals("t_user", TenantTableWhitelist.normalize(" app.t_user "));
    }

    @Test
    @DisplayName("合并后刷新租户行处理器，模块自表立即进入忽略集")
    void refreshesTenantLineHandler() {
        MybatisProperties mybatisProperties = new MybatisProperties();
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(mybatisProperties);
        mybatisProperties.getTenant().getIgnoreTables().add("t_tenant_self");
        assertFalse(handler.ignoreTable("t_tenant_self"));

        new TenantTableWhitelist(List.of(), List.of(), mybatisProperties, handler).afterPropertiesSet();

        assertTrue(handler.ignoreTable("t_tenant_self"));
    }

    @Test
    @DisplayName("缺少 MyBatis 属性时依然无条件刷新租户行处理器")
    void refreshesTenantLineHandlerWithoutMybatisProperties() {
        MybatisProperties mybatisProperties = new MybatisProperties();
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(mybatisProperties);
        mybatisProperties.getTenant().getIgnoreTables().add("t_tenant_orphan");
        assertFalse(handler.ignoreTable("t_tenant_orphan"));

        new TenantTableWhitelist(List.of(), List.of(), null, handler).afterPropertiesSet();

        assertTrue(handler.ignoreTable("t_tenant_orphan"));
    }

    @Test
    @DisplayName("缺少 MyBatis 属性时不抛异常，白名单仍可判定")
    void worksWithoutMybatisProperties() {
        TenantTableWhitelist whitelist = new TenantTableWhitelist(new ArrayList<>(List.of("t_tenant")),
                new ArrayList<>(), null);
        whitelist.afterPropertiesSet();
        assertTrue(whitelist.isIgnored("t_tenant"));
        assertTrue(new TenantTableWhitelist(null, null, null).tables().isEmpty());
    }
}
