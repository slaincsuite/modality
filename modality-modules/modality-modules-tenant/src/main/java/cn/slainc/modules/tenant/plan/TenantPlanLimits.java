package cn.slainc.modules.tenant.plan;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

// 套餐生效值：未配置的维度在解析时已回落为全局默认；数值为 null 或 <=0 表示不限制，planId 为 null 表示未绑定套餐
public record TenantPlanLimits(Long tenantId, Long planId, String planCode, Integer maxUsers, Long storageQuotaMb,
                               Long maxFileSizeMb, Integer maxExportRows, Integer logRetentionDays,
                               Set<TenantPlanFeature> features, Integer rateLimitPerMinute,
                               Integer maxConcurrentExports, Integer searchMaxPageSize, TenantPlanDataScope dataScope,
                               List<Long> menuIds) implements Serializable {

    private static final long serialVersionUID = 1L;

    public boolean limitUsers() {
        return maxUsers != null && maxUsers > 0;
    }

    public boolean limitStorage() {
        return storageQuotaMb != null && storageQuotaMb > 0;
    }

    public boolean limitFileSize() {
        return maxFileSizeMb != null && maxFileSizeMb > 0;
    }

    // 功能是否放行：未配置功能清单（null 或空）表示不做限制
    public boolean allows(TenantPlanFeature feature) {
        return feature == null || features == null || features.isEmpty() || features.contains(feature);
    }

    // 套餐是否限定了菜单范围；modality 菜单为平台级，该范围仅作生效力输出，不落租户授权
    public boolean restrictsMenus() {
        return menuIds != null && !menuIds.isEmpty();
    }

    public Integer exportRowsLimit() {
        return maxExportRows == null || maxExportRows <= 0 ? null : maxExportRows;
    }

    public Integer concurrentExportsLimit() {
        return maxConcurrentExports == null || maxConcurrentExports <= 0 ? null : maxConcurrentExports;
    }

    public Integer searchPageSizeLimit() {
        return searchMaxPageSize == null || searchMaxPageSize <= 0 ? null : searchMaxPageSize;
    }

    public Integer rateLimit() {
        return rateLimitPerMinute == null || rateLimitPerMinute <= 0 ? null : rateLimitPerMinute;
    }

    public Long storageQuotaBytes() {
        return limitStorage() ? storageQuotaMb * 1024L * 1024L : null;
    }

    public Long maxFileSizeBytes() {
        return limitFileSize() ? maxFileSizeMb * 1024L * 1024L : null;
    }
}
