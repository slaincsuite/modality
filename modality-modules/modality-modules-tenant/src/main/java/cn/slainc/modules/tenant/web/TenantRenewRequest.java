package cn.slainc.modules.tenant.web;

import java.time.LocalDateTime;

public record TenantRenewRequest(LocalDateTime expireAt) {
}
