package cn.slainc.modules.tenant.dto;

import cn.slainc.modules.tenant.entity.TenantPlanRecord;
import cn.slainc.modules.tenant.plan.TenantPlanDataScope;
import cn.slainc.modules.tenant.plan.TenantPlanFeature;

import java.util.Set;

public record TenantPlanView(Long id, String planCode, String planName, Integer maxUsers, Long storageQuotaMb,
                             Long maxFileSizeMb, Integer maxExportRows, Integer logRetentionDays,
                             Set<TenantPlanFeature> features, Integer rateLimitPerMinute,
                             Integer maxConcurrentExports, Integer searchMaxPageSize,
                             TenantPlanDataScope dataScope, Integer sort, Boolean enabled, String remark,
                             Long createTime, Long updateTime) {

    public static TenantPlanView from(TenantPlanRecord record) {
        return new TenantPlanView(record.getId(), record.getPlanCode(), record.getPlanName(), record.getMaxUsers(),
                record.getStorageQuotaMb(), record.getMaxFileSizeMb(), record.getMaxExportRows(),
                record.getLogRetentionDays(), record.features(), record.getRateLimitPerMinute(),
                record.getMaxConcurrentExports(), record.getSearchMaxPageSize(), record.dataScope(),
                record.getSort(), record.getEnabled(), record.getRemark(), record.getCreateTime(),
                record.getUpdateTime());
    }
}
