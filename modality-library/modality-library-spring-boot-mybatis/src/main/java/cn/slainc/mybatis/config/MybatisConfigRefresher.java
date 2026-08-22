package cn.slainc.mybatis.config;

import cn.slainc.core.event.ConfigChangedEvent;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MybatisConfigRefresher {

    private static final Logger log = LoggerFactory.getLogger(MybatisConfigRefresher.class);

    public static final String KEY_SQL_LOG_ENABLED = "mybatis.sql-log.enabled";

    public static final String KEY_SLOW_THRESHOLD = "mybatis.sql-log.slow-threshold";

    public static final String KEY_TENANT_ENABLED = "mybatis.tenant.enabled";

    public static final String KEY_TENANT_IGNORE_TABLES = "mybatis.tenant.ignore-tables";

    private final MybatisProperties properties;

    private final DefaultTenantLineHandler tenantLineHandler;

    public MybatisConfigRefresher(MybatisProperties properties,
                                          DefaultTenantLineHandler tenantLineHandler) {
        this.properties = properties;
        this.tenantLineHandler = tenantLineHandler;
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        apply(event.getConfigKey(), event.getNewValue(), event.isRemoved());
    }

    public void apply(String key, String value, boolean removed) {
        if (key == null) {
            return;
        }
        switch (key) {
            case KEY_SQL_LOG_ENABLED -> properties.getSqlLog().setEnabled(!removed && Boolean.parseBoolean(value));
            case KEY_SLOW_THRESHOLD -> {
                if (!removed) {
                    properties.getSqlLog().setSlowThreshold(Long.parseLong(value));
                }
            }
            case KEY_TENANT_ENABLED -> properties.getTenant().setEnabled(!removed && Boolean.parseBoolean(value));
            case KEY_TENANT_IGNORE_TABLES -> properties.getTenant().setIgnoreTables(parseList(value, removed));
            default -> {
                return;
            }
        }
        tenantLineHandler.refresh();
        log.info("MyBatis 配置已热更新: {} = {}", key, value);
    }

    private static List<String> parseList(String value, boolean removed) {
        if (removed || value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList());
    }
}
