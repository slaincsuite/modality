package cn.slainc.mybatis.tenant;

import cn.slainc.mybatis.properties.MybatisProperties;

import java.util.Collection;
import java.util.List;

public final class TenantIgnoreTables {

    private TenantIgnoreTables() {
    }

    public static void merge(Collection<String> tables, MybatisProperties properties,
                             DefaultTenantLineHandler handler) {
        if (properties == null) {
            return;
        }
        List<String> ignoreTables = properties.getTenant().getIgnoreTables();
        for (String table : tables) {
            if (table != null && !table.isBlank()
                    && ignoreTables.stream().noneMatch(current -> current != null
                            && current.trim().equalsIgnoreCase(table))) {
                ignoreTables.add(table);
            }
        }
        if (handler != null) {
            handler.refresh();
        }
    }
}
