package cn.slainc.tenant.support;

import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import cn.slainc.mybatis.tenant.TenantIgnoreTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TenantTableWhitelist implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TenantTableWhitelist.class);

    private final List<String> tables = new ArrayList<>();

    private final List<String> prefixes = new ArrayList<>();

    private final MybatisProperties mybatisProperties;

    private final DefaultTenantLineHandler tenantLineHandler;

    public TenantTableWhitelist(List<String> tables, List<String> prefixes,
                                MybatisProperties mybatisProperties) {
        this(tables, prefixes, mybatisProperties, null);
    }

    public TenantTableWhitelist(List<String> tables, List<String> prefixes,
                                MybatisProperties mybatisProperties, DefaultTenantLineHandler tenantLineHandler) {
        merge(this.tables, tables);
        merge(this.prefixes, prefixes);
        this.mybatisProperties = mybatisProperties;
        this.tenantLineHandler = tenantLineHandler;
    }

    @Override
    public void afterPropertiesSet() {
        TenantIgnoreTables.merge(tables, mybatisProperties, tenantLineHandler);
        if (mybatisProperties != null) {
            MybatisProperties.Tenant tenant = mybatisProperties.getTenant();
            merge(tenant.getIgnoreTablePrefixes(), prefixes);
        }
        if (tenantLineHandler != null) {
            tenantLineHandler.refresh();
        }
        if (mybatisProperties == null) {
            log.debug("未启用 MyBatis 租户条件，租户表白名单仅在模块内生效");
            return;
        }
        MybatisProperties.Tenant tenant = mybatisProperties.getTenant();
        log.info("租户表白名单已合并到 MyBatis 配置，当前共 {} 张表、{} 个前缀",
                tenant.getIgnoreTables().size(), tenant.getIgnoreTablePrefixes().size());
    }

    public boolean isIgnored(String tableName) {
        String table = normalize(tableName);
        if (table.isEmpty()) {
            return false;
        }
        for (String item : tables) {
            if (normalize(item).equals(table)) {
                return true;
            }
        }
        for (String prefix : prefixes) {
            String normalized = normalize(prefix);
            if (!normalized.isEmpty() && table.startsWith(normalized)) {
                return true;
            }
        }
        return false;
    }

    public List<String> tables() {
        return List.copyOf(tables);
    }

    public List<String> prefixes() {
        return List.copyOf(prefixes);
    }

    public static String normalize(String tableName) {
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

    private static void merge(List<String> target, List<String> source) {
        if (target == null || source == null) {
            return;
        }
        for (String item : source) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String value = item.trim();
            boolean exists = target.stream()
                    .anyMatch(current -> current != null && current.trim().equalsIgnoreCase(value));
            if (!exists) {
                target.add(value);
            }
        }
    }
}
