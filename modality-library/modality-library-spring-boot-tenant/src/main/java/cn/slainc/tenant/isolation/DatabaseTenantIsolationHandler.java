package cn.slainc.tenant.isolation;

import cn.slainc.core.exception.BizException;
import cn.slainc.datasource.definition.DataSourceDefinition;
import cn.slainc.datasource.enums.DatabaseType;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class DatabaseTenantIsolationHandler implements TenantIsolationHandler {

    public static final String NAME_PREFIX = "tenant_";

    private static final Logger log = LoggerFactory.getLogger(DatabaseTenantIsolationHandler.class);

    private final DynamicDataSourceManager dataSourceManager;

    private final DatabaseIsolationTemplate template;

    public DatabaseTenantIsolationHandler(DynamicDataSourceManager dataSourceManager,
                                          DatabaseIsolationTemplate template) {
        this.dataSourceManager = dataSourceManager;
        this.template = template == null
                ? new DatabaseIsolationTemplate(null, null, null, null, null) : template;
    }

    @Override
    public boolean supports(TenantIsolationStrategy strategy) {
        return strategy == TenantIsolationStrategy.DATABASE;
    }

    @Override
    public void apply(TenantInfo tenant) {
        String name = dataSourceName(tenant.getTenantId());
        tenant.setDatasourceName(name);
        tenant.putMetadata(TenantInfo.METADATA_DATASOURCE, name);
        if (dataSourceManager == null) {
            log.warn("未配置动态数据源管理器，跳过租户 [{}] 的独立数据源注册：{}", tenant.getTenantId(), name);
            return;
        }
        if (dataSourceManager.exists(name)) {
            log.debug("租户 [{}] 的数据源 [{}] 已存在，无需重复注册", tenant.getTenantId(), name);
            return;
        }
        if (!template.isConfigured()) {
            throw new BizException("未配置租户独立库连接模板：modality.tenant.isolation.database.url-template");
        }
        dataSourceManager.register(definition(tenant, name));
        log.info("租户 [{}] 的独立数据源 [{}] 已注册", tenant.getTenantId(), name);
    }

    @Override
    public void remove(TenantInfo tenant) {
        String name = dataSourceName(tenant.getTenantId());
        if (dataSourceManager == null || !dataSourceManager.exists(name)) {
            log.warn("租户 [{}] 的数据源 [{}] 不存在，无需清理", tenant.getTenantId(), name);
            return;
        }
        dataSourceManager.remove(name);
        log.info("租户 [{}] 的独立数据源 [{}] 已移除", tenant.getTenantId(), name);
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public String describe() {
        return "库级隔离：为每个租户注册名为 tenant_<租户ID> 的独立数据源，并通过 DynamicDataSourceContextHolder 切换";
    }

    public String dataSourceName(Long tenantId) {
        if (tenantId == null) {
            throw new BizException("租户 ID 不能为空");
        }
        return NAME_PREFIX + tenantId;
    }

    public <T> T callWith(Long tenantId, Supplier<T> supplier) {
        DynamicDataSourceContextHolder.push(dataSourceName(tenantId));
        try {
            return supplier.get();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    public void runWith(Long tenantId, Runnable runnable) {
        callWith(tenantId, () -> {
            runnable.run();
            return null;
        });
    }

    public String current() {
        return DynamicDataSourceContextHolder.peek();
    }

    public DataSourceDefinition definition(TenantInfo tenant, String name) {
        DataSourceDefinition definition = new DataSourceDefinition();
        definition.setName(name);
        definition.setGroupName(template.resolveGroupName());
        definition.setUrl(template.renderUrl(tenant));
        definition.setUsername(template.renderUsername(tenant));
        definition.setPassword(template.renderPassword(tenant));
        definition.setDriverClassName(template.driverClassName());
        definition.setDatabaseType(DatabaseType.resolve(definition.getUrl(), template.driverClassName()));
        definition.setDescription("租户 [" + tenant.getTenantCode() + "] 独立数据源");
        return definition;
    }
}
