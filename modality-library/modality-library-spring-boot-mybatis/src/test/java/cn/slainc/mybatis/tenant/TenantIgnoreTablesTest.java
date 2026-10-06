package cn.slainc.mybatis.tenant;

import cn.slainc.mybatis.properties.MybatisProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class TenantIgnoreTablesTest {

    @Test
    @DisplayName("并入新表后忽略集立即生效且 refresh 被调用")
    void mergeAddsNewTableAndRefreshesHandler() {
        MybatisProperties properties = new MybatisProperties();
        DefaultTenantLineHandler handler = spy(new DefaultTenantLineHandler(properties));

        TenantIgnoreTables.merge(List.of("t_api_key"), properties, handler);

        assertTrue(properties.getTenant().getIgnoreTables().contains("t_api_key"));
        assertTrue(handler.ignoreTable("t_api_key"));
        verify(handler).refresh();
    }

    @Test
    @DisplayName("重复并入同一张表不产生重复项")
    void repeatedMergeKeepsSingleEntry() {
        MybatisProperties properties = new MybatisProperties();

        TenantIgnoreTables.merge(List.of("t_audit_log"), properties, null);
        TenantIgnoreTables.merge(List.of("t_audit_log"), properties, null);

        assertEquals(1, properties.getTenant().getIgnoreTables().stream()
                .filter("t_audit_log"::equals).count());
    }

    @Test
    @DisplayName("已存在（忽略大小写）与空白表名不重复并入")
    void mergeSkipsExistingAndBlankEntries() {
        MybatisProperties properties = new MybatisProperties();

        TenantIgnoreTables.merge(Arrays.asList("T_DYNAMIC_CONFIG", " ", null), properties, null);

        assertEquals(1, properties.getTenant().getIgnoreTables().stream()
                .filter("t_dynamic_config"::equalsIgnoreCase).count());
    }

    @Test
    @DisplayName("properties 缺席时不做任何并入")
    void mergeWithoutPropertiesDoesNothing() {
        MybatisProperties properties = new MybatisProperties();
        int before = properties.getTenant().getIgnoreTables().size();

        TenantIgnoreTables.merge(List.of("t_open_app"), null, null);

        assertEquals(before, properties.getTenant().getIgnoreTables().size());
    }
}
