package cn.slainc.apikey.autoconfigure;

import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

public class ApiKeyTableWhitelist implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyTableWhitelist.class);

    private final List<String> tables;

    private final MybatisProperties mybatisProperties;

    private final DefaultTenantLineHandler tenantLineHandler;

    public ApiKeyTableWhitelist(List<String> tables, MybatisProperties mybatisProperties,
                                DefaultTenantLineHandler tenantLineHandler) {
        this.tables = tables;
        this.mybatisProperties = mybatisProperties;
        this.tenantLineHandler = tenantLineHandler;
    }

    @Override
    public void afterPropertiesSet() {
        if (mybatisProperties == null) {
            log.debug("未启用 MyBatis 租户条件，API Key 表白名单仅在模块内生效");
            return;
        }
        List<String> ignoreTables = mybatisProperties.getTenant().getIgnoreTables();
        for (String table : tables) {
            if (table != null && !table.isBlank()
                    && ignoreTables.stream().noneMatch(current -> current != null
                    && current.trim().equalsIgnoreCase(table))) {
                ignoreTables.add(table);
            }
        }
        // SqlSessionFactory 可能已提前创建，租户处理器在合并前就读取了忽略表快照，这里强制重建一次
        if (tenantLineHandler != null) {
            tenantLineHandler.refresh();
        }
    }
}
