package cn.slainc.modules.tenant.dto;

import java.util.List;

// 套餐默认菜单范围入参（覆盖式）；modality 菜单为平台级表，此范围不落租户授权
public record TenantPlanMenuForm(List<Long> menuIds) {
}
