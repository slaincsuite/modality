package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import cn.slainc.mybatis.tenant.TenantIgnoreTables;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;

public class InfraTableWhitelist implements InitializingBean {

    private final MybatisProperties properties;

    private final DefaultTenantLineHandler tenantLineHandler;

    public InfraTableWhitelist(MybatisProperties properties, DefaultTenantLineHandler tenantLineHandler) {
        this.properties = properties;
        this.tenantLineHandler = tenantLineHandler;
    }

    @Override
    public void afterPropertiesSet() {
        if (properties == null) {
            return;
        }
        TenantIgnoreTables.merge(List.of(InfraKeys.API_KEY_ROTATION_TABLE, InfraKeys.API_KEY_METRIC_TABLE),
                properties, tenantLineHandler);
    }
}
