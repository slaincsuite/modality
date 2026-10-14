package cn.slainc.modules.tenant.entity;

import cn.slainc.modules.tenant.TenantKeys;
import cn.slainc.modules.tenant.plan.TenantPlanDataScope;
import cn.slainc.modules.tenant.plan.TenantPlanFeature;
import cn.slainc.utils.json.JsonUtils;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.LinkedHashSet;
import java.util.Set;

// 租户套餐模板：额度、功能开关、数据权限与默认菜单范围；null 表示该维度不限制 / 回落全局默认
@TableName(TenantPlanRecord.TABLE_NAME)
public class TenantPlanRecord {

    public static final String TABLE_NAME = TenantKeys.PLAN_TABLE;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String planCode;

    private String planName;

    private Integer maxUsers;

    private Long storageQuotaMb;

    private Long maxFileSizeMb;

    private Integer maxExportRows;

    private Integer logRetentionDays;

    // 功能开关集合的 JSON 数组形式，如 ["SEARCH","EXPORT"]；null 或空数组表示不做功能限制
    private String features;

    private Integer rateLimitPerMinute;

    private Integer maxConcurrentExports;

    private Integer searchMaxPageSize;

    private String dataScope;

    private Integer sort = 0;

    private Boolean enabled = Boolean.TRUE;

    private String remark;

    private Long createTime;

    private Long updateTime;

    public Set<TenantPlanFeature> features() {
        if (features == null || features.isBlank()) {
            return null;
        }
        try {
            Set<TenantPlanFeature> parsed = new LinkedHashSet<>(JsonUtils.parseList(features,
                    TenantPlanFeature.class));
            return parsed.isEmpty() ? null : parsed;
        } catch (Exception e) {
            return null;
        }
    }

    public void features(Set<TenantPlanFeature> featureSet) {
        this.features = featureSet == null || featureSet.isEmpty()
                ? null
                : JsonUtils.toJson(new LinkedHashSet<>(featureSet));
    }

    public TenantPlanDataScope dataScope() {
        if (dataScope == null || dataScope.isBlank()) {
            return null;
        }
        try {
            return TenantPlanDataScope.valueOf(dataScope.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void dataScope(TenantPlanDataScope scope) {
        this.dataScope = scope == null ? null : scope.name();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }

    public Long getStorageQuotaMb() {
        return storageQuotaMb;
    }

    public void setStorageQuotaMb(Long storageQuotaMb) {
        this.storageQuotaMb = storageQuotaMb;
    }

    public Long getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(Long maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public Integer getMaxExportRows() {
        return maxExportRows;
    }

    public void setMaxExportRows(Integer maxExportRows) {
        this.maxExportRows = maxExportRows;
    }

    public Integer getLogRetentionDays() {
        return logRetentionDays;
    }

    public void setLogRetentionDays(Integer logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    public Integer getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(Integer rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public Integer getMaxConcurrentExports() {
        return maxConcurrentExports;
    }

    public void setMaxConcurrentExports(Integer maxConcurrentExports) {
        this.maxConcurrentExports = maxConcurrentExports;
    }

    public Integer getSearchMaxPageSize() {
        return searchMaxPageSize;
    }

    public void setSearchMaxPageSize(Integer searchMaxPageSize) {
        this.searchMaxPageSize = searchMaxPageSize;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
}
