package cn.slainc.modules.tenant.dto;

import java.time.LocalDateTime;

public record TenantRenewRequest(LocalDateTime expireAt) {
}
