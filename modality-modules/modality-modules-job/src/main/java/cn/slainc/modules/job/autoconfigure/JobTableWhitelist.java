package cn.slainc.modules.job.autoconfigure;

import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import cn.slainc.mybatis.tenant.TenantIgnoreTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

// 任务定义与执行日志是集群级全局表：按租户保存 tenant_id 但不做租户行过滤
public class JobTableWhitelist implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JobTableWhitelist.class);

    private final List<String> tables;

    private final MybatisProperties mybatisProperties;

    private final DefaultTenantLineHandler tenantLineHandler;

    public JobTableWhitelist(List<String> tables, MybatisProperties mybatisProperties,
                             DefaultTenantLineHandler tenantLineHandler) {
        this.tables = tables;
        this.mybatisProperties = mybatisProperties;
        this.tenantLineHandler = tenantLineHandler;
    }

    @Override
    public void afterPropertiesSet() {
        if (mybatisProperties == null) {
            log.debug("未启用 MyBatis 租户条件，任务表白名单仅在模块内生效");
            return;
        }
        TenantIgnoreTables.merge(tables, mybatisProperties, tenantLineHandler);
    }
}
