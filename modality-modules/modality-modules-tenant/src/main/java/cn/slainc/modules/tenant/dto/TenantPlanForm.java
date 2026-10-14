package cn.slainc.modules.tenant.dto;

import cn.slainc.modules.tenant.plan.TenantPlanDataScope;
import cn.slainc.modules.tenant.plan.TenantPlanFeature;

import java.util.Set;

// 套餐模板新增 / 修改入参；各额度维度为 null 或 <=0 表示不限制，未配维度在生效值里回落全局默认
public record TenantPlanForm(String planCode, String planName, Integer maxUsers, Long storageQuotaMb,
                             Long maxFileSizeMb, Integer maxExportRows, Integer logRetentionDays,
                             Set<TenantPlanFeature> features, Integer rateLimitPerMinute,
                             Integer maxConcurrentExports, Integer searchMaxPageSize,
                             TenantPlanDataScope dataScope, Integer sort, Boolean enabled, String remark) {
}
