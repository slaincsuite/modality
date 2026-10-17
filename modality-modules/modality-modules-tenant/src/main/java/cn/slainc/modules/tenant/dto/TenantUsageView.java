package cn.slainc.modules.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TenantUsageView(
        @Schema(description = "租户 ID")
        Long tenantId,
        @Schema(description = "用户数配额上限，小于等于 0 表示不限制")
        long maxUsers,
        @Schema(description = "存储配额上限，小于等于 0 表示不限制")
        long maxStorage,
        @Schema(description = "接口 QPS 配额上限，小于等于 0 表示不限制")
        long maxApiQps,
        @Schema(description = "接口日调用量配额上限，小于等于 0 表示不限制")
        long maxApiDaily,
        @Schema(description = "已用用户数；未装配 UserUsageProvider 或查询失败时为 null")
        Long userCount,
        @Schema(description = "启用状态用户数；未装配 UserUsageProvider 或查询失败时为 null")
        Long activeUserCount,
        @Schema(description = "停用状态用户数；未装配 UserUsageProvider 或查询失败时为 null")
        Long disabledUserCount) {
}
