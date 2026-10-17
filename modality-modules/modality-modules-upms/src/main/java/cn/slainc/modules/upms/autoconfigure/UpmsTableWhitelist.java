package cn.slainc.modules.upms.autoconfigure;

import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import cn.slainc.mybatis.tenant.TenantIgnoreTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

// 权限/菜单/系统注册/操作日志/登录记录/用户设置是平台级全局表，需并入租户忽略集并强制重建快照
public class UpmsTableWhitelist implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(UpmsTableWhitelist.class);

    private final List<String> tables;

    private final MybatisProperties mybatisProperties;

    private final DefaultTenantLineHandler tenantLineHandler;

    public UpmsTableWhitelist(List<String> tables, MybatisProperties mybatisProperties,
                              DefaultTenantLineHandler tenantLineHandler) {
        this.tables = tables;
        this.mybatisProperties = mybatisProperties;
        this.tenantLineHandler = tenantLineHandler;
    }

    @Override
    public void afterPropertiesSet() {
        if (mybatisProperties == null) {
            log.debug("未启用 MyBatis 租户条件，UPMS 表白名单仅在模块内生效");
            return;
        }
        TenantIgnoreTables.merge(tables, mybatisProperties, tenantLineHandler);
    }
}
