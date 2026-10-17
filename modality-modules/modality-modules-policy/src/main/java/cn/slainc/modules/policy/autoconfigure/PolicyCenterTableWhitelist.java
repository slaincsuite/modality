package cn.slainc.modules.policy.autoconfigure;

import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import cn.slainc.mybatis.tenant.TenantIgnoreTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

public class PolicyCenterTableWhitelist implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PolicyCenterTableWhitelist.class);

    private final List<String> tables;

    private final MybatisProperties mybatisProperties;

    private final DefaultTenantLineHandler tenantLineHandler;

    public PolicyCenterTableWhitelist(List<String> tables, MybatisProperties mybatisProperties,
                                      DefaultTenantLineHandler tenantLineHandler) {
        this.tables = tables;
        this.mybatisProperties = mybatisProperties;
        this.tenantLineHandler = tenantLineHandler;
    }

    @Override
    public void afterPropertiesSet() {
        if (mybatisProperties == null) {
            log.debug("未启用 MyBatis 租户条件，策略中心平台表白名单仅在模块内生效");
            return;
        }
        TenantIgnoreTables.merge(tables, mybatisProperties, tenantLineHandler);
    }
}
