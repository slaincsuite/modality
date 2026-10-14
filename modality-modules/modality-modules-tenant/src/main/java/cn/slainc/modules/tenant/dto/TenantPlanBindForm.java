package cn.slainc.modules.tenant.dto;

// 租户绑定套餐入参；planId 为空表示解绑（该租户全部回落全局默认值）
public record TenantPlanBindForm(Long planId) {
}
