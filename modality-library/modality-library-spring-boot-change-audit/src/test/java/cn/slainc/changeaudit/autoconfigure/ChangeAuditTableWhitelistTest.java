package cn.slainc.changeaudit.autoconfigure;

import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class ChangeAuditTableWhitelistTest {

    @Test
    @DisplayName("变更审计表按平台表并入租户忽略集并刷新拦截器")
    void registersTheTableAsPlatformTable() {
        MybatisProperties properties = new MybatisProperties();
        DefaultTenantLineHandler handler = spy(new DefaultTenantLineHandler(properties));

        new ChangeAuditTableWhitelist(List.of("t_change_audit_record"), properties, handler).afterPropertiesSet();

        assertTrue(properties.getTenant().getIgnoreTables().contains("t_change_audit_record"));
        assertTrue(handler.ignoreTable("t_change_audit_record"));
        verify(handler).refresh();
    }

    @Test
    @DisplayName("未启用 MyBatis 租户条件时静默跳过登记")
    void skipsWhenMybatisIsAbsent() {
        ChangeAuditTableWhitelist whitelist = new ChangeAuditTableWhitelist(List.of("t_change_audit_record"), null,
                null);

        assertDoesNotThrow(whitelist::afterPropertiesSet);
    }
}
