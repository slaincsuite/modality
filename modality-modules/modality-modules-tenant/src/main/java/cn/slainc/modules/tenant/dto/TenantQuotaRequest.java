package cn.slainc.modules.tenant.dto;

// 四个维度均为可选：null 表示该维度维持原值不变，<=0 表示不限制
public record TenantQuotaRequest(Long maxUsers, Long maxStorage, Long maxApiQps, Long maxApiDaily) {
}
