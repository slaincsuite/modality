package cn.slainc.tenant.isolation;

import cn.slainc.core.config.DynamicConfigService;

public class TenantIsolationStrategyResolver {

    public static final String STRATEGY_KEY = "tenant.isolation.strategy";

    private final TenantIsolationStrategy defaultStrategy;

    private final DynamicConfigService configService;

    public TenantIsolationStrategyResolver(TenantIsolationStrategy defaultStrategy,
                                           DynamicConfigService configService) {
        this.defaultStrategy = defaultStrategy == null ? TenantIsolationStrategy.ROW : defaultStrategy;
        this.configService = configService;
    }

    public TenantIsolationStrategy resolve(Long tenantId) {
        if (configService != null) {
            if (tenantId != null) {
                TenantIsolationStrategy perTenant = read(STRATEGY_KEY + "." + tenantId);
                if (perTenant != null) {
                    return perTenant;
                }
            }
            TenantIsolationStrategy global = read(STRATEGY_KEY);
            if (global != null) {
                return global;
            }
        }
        return defaultStrategy;
    }

    public TenantIsolationStrategy defaultStrategy() {
        return defaultStrategy;
    }

    private TenantIsolationStrategy read(String key) {
        return configService.findString(key).map(TenantIsolationStrategy::of).orElse(null);
    }
}
