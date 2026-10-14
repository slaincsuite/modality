package cn.slainc.modules.tenant.plan;

// 套餐可开关的功能；features 为空表示不做功能限制，非空则只放行列出的功能
public enum TenantPlanFeature {

    MFA,

    SEARCH,

    OPEN,

    MESSAGE,

    JOB,

    EXPORT,

    RECYCLE,

    AUDIT,

    WORKORDER
}
