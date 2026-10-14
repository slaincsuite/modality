package cn.slainc.modules.tenant.plan;

// 套餐里的数据权限范围；null 表示沿用现有数据权限逻辑
public enum TenantPlanDataScope {

    ALL,

    DEPT_AND_CHILD,

    DEPT,

    SELF
}
