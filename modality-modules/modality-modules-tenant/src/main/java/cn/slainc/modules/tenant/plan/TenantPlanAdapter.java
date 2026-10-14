package cn.slainc.modules.tenant.plan;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.modules.tenant.autoconfigure.TenantModuleProperties;
import cn.slainc.modules.tenant.entity.TenantPlanMenuRecord;
import cn.slainc.modules.tenant.entity.TenantPlanRecord;
import cn.slainc.modules.tenant.mapper.TenantPlanMapper;
import cn.slainc.modules.tenant.mapper.TenantPlanMenuMapper;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.tenant.mapper.TenantMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

// 套餐生效值解析：租户 -> 套餐 -> 逐维度回落全局默认（动态配置优先，缺失或异常回落静态默认），结果做 60 秒进程内缓存
public class TenantPlanAdapter implements TenantPlanProvider {

    private static final long CACHE_TTL_NANOS = Duration.ofSeconds(60).toNanos();

    private static final String CONFIG_PREFIX = "modality.tenant-module.plan.";

    private final ObjectProvider<TenantMapper> tenantMapperProvider;

    private final ObjectProvider<TenantPlanMapper> planMapperProvider;

    private final ObjectProvider<TenantPlanMenuMapper> planMenuMapperProvider;

    private final ObjectProvider<DynamicConfigService> configServiceProvider;

    private final TenantModuleProperties properties;

    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public TenantPlanAdapter(ObjectProvider<TenantMapper> tenantMapperProvider,
                             ObjectProvider<TenantPlanMapper> planMapperProvider,
                             ObjectProvider<TenantPlanMenuMapper> planMenuMapperProvider,
                             ObjectProvider<DynamicConfigService> configServiceProvider,
                             TenantModuleProperties properties) {
        this.tenantMapperProvider = tenantMapperProvider;
        this.planMapperProvider = planMapperProvider;
        this.planMenuMapperProvider = planMenuMapperProvider;
        this.configServiceProvider = configServiceProvider;
        this.properties = properties;
    }

    @Override
    public TenantPlanLimits load(Long tenantId) {
        if (tenantId == null) {
            return limits(null, null, List.of());
        }
        Cached cached = cache.get(tenantId);
        if (cached != null && !cached.expired()) {
            return cached.limits();
        }
        TenantPlanLimits limits = resolve(tenantId);
        cache.put(tenantId, new Cached(limits, System.nanoTime()));
        return limits;
    }

    public void evict(Long tenantId) {
        if (tenantId == null) {
            cache.clear();
        } else {
            cache.remove(tenantId);
        }
    }

    public void evictAll() {
        cache.clear();
    }

    private TenantPlanLimits resolve(Long tenantId) {
        TenantMapper tenantMapper = tenantMapperProvider.getIfAvailable();
        Tenant tenant = tenantMapper == null ? null : tenantMapper.selectById(tenantId);
        TenantPlanRecord plan = tenant == null ? null : plan(tenant.getPackageId());
        List<Long> menuIds = plan == null ? List.of() : menuIds(plan.getId());
        return limits(tenantId, plan, menuIds);
    }

    private TenantPlanRecord plan(Long planId) {
        TenantPlanMapper planMapper = planId == null ? null : planMapperProvider.getIfAvailable();
        TenantPlanRecord plan = planMapper == null ? null : planMapper.selectById(planId);
        // 停用的套餐按「未绑定」处理，避免停用后额度仍然生效
        return plan == null || Boolean.FALSE.equals(plan.getEnabled()) ? null : plan;
    }

    private List<Long> menuIds(Long planId) {
        TenantPlanMenuMapper mapper = planId == null ? null : planMenuMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<TenantPlanMenuRecord>lambdaQuery()
                        .eq(TenantPlanMenuRecord::getPlanId, planId)
                        .orderByAsc(TenantPlanMenuRecord::getMenuId)).stream()
                .map(TenantPlanMenuRecord::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    // 套餐已配置的维度用套餐值，其余回落全局默认；用户数 / 存储配额 / 导出条数与数据权限范围没有全局默认，保持 null
    private TenantPlanLimits limits(Long tenantId, TenantPlanRecord plan, List<Long> menuIds) {
        return new TenantPlanLimits(tenantId,
                plan == null ? null : plan.getId(),
                plan == null ? null : plan.getPlanCode(),
                plan == null ? null : plan.getMaxUsers(),
                plan == null ? null : plan.getStorageQuotaMb(),
                value(plan == null ? null : plan.getMaxFileSizeMb(), configMaxFileSizeMb()),
                plan == null ? null : plan.getMaxExportRows(),
                value(plan == null ? null : plan.getLogRetentionDays(), configInt("log-retention-days",
                        defaults().getLogRetentionDays())),
                plan == null ? null : plan.features(),
                value(plan == null ? null : plan.getRateLimitPerMinute(), configInt("rate-limit-per-minute",
                        defaults().getRateLimitPerMinute())),
                value(plan == null ? null : plan.getMaxConcurrentExports(), configInt("max-concurrent-exports",
                        defaults().getMaxConcurrentExports())),
                value(plan == null ? null : plan.getSearchMaxPageSize(), configInt("search-max-page-size",
                        defaults().getSearchMaxPageSize())),
                plan == null ? null : plan.dataScope(),
                menuIds);
    }

    private static <T> T value(T configured, T fallback) {
        if (configured instanceof Integer number) {
            return number > 0 ? configured : fallback;
        }
        if (configured instanceof Long number) {
            return number > 0 ? configured : fallback;
        }
        return configured != null ? configured : fallback;
    }

    private TenantModuleProperties.PlanDefaults defaults() {
        return properties == null ? new TenantModuleProperties.PlanDefaults() : properties.getPlan();
    }

    private Long configMaxFileSizeMb() {
        long configured = configLong("max-file-size-mb", defaults().getMaxFileSizeMb());
        return configured <= 0 ? null : configured;
    }

    private long configLong(String key, long defaultValue) {
        DynamicConfigService service = configServiceProvider == null ? null : configServiceProvider.getIfAvailable();
        if (service == null) {
            return defaultValue;
        }
        try {
            return service.getLong(CONFIG_PREFIX + key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int configInt(String key, int defaultValue) {
        DynamicConfigService service = configServiceProvider == null ? null : configServiceProvider.getIfAvailable();
        if (service == null) {
            return defaultValue;
        }
        try {
            return service.getInt(CONFIG_PREFIX + key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private record Cached(TenantPlanLimits limits, long stamp) {

        boolean expired() {
            return System.nanoTime() - stamp > CACHE_TTL_NANOS;
        }
    }
}
