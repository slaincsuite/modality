package cn.slainc.mybatis.tenant;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.mybatis.properties.MybatisProperties;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DefaultTenantLineHandler implements TenantLineHandler {

    private final MybatisProperties properties;

    private volatile Set<String> ignoreTables = Set.of();

    private volatile List<String> ignorePrefixes = List.of();

    public DefaultTenantLineHandler(MybatisProperties properties) {
        this.properties = properties;
        refresh();
    }

    public final void refresh() {
        Set<String> tables = new HashSet<>();
        for (String table : properties.getTenant().getIgnoreTables()) {
            if (table != null && !table.isBlank()) {
                tables.add(table.toLowerCase(Locale.ROOT));
            }
        }
        this.ignoreTables = Set.copyOf(tables);
        this.ignorePrefixes = List.copyOf(properties.getTenant().getIgnoreTablePrefixes());
    }

    @Override
    public Expression getTenantId() {
        return new LongValue(tenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return properties.getTenant().getColumn();
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (!properties.getTenant().isEnabled() || TenantHelper.isIgnored()) {
            return true;
        }
        if (properties.getTenant().isSuperAdminIgnores() && UserContext.isSuperAdmin()) {
            return true;
        }
        String table = normalize(tableName);
        if (ignoreTables.contains(table)) {
            return true;
        }
        for (String prefix : ignorePrefixes) {
            if (prefix != null && !prefix.isBlank() && table.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public long tenantId() {
        Long tenantId = TenantContext.get();
        return tenantId == null ? TenantContext.DEFAULT_TENANT_ID : tenantId;
    }

    private static String normalize(String tableName) {
        if (tableName == null) {
            return "";
        }
        String table = tableName.trim().toLowerCase(Locale.ROOT);
        int dot = table.lastIndexOf('.');
        if (dot >= 0) {
            table = table.substring(dot + 1);
        }
        return table.replace("`", "").replace("\"", "");
    }
}
