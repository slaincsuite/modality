package cn.slainc.modules.tenant.plan;

// 租户套餐端口：按租户取生效值；未绑定套餐时返回 planId 为空的默认生效值，不返回 null
public interface TenantPlanProvider {

    TenantPlanLimits load(Long tenantId);
}
