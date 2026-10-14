package cn.slainc.modules.tenant.dto;

import java.time.LocalDateTime;

public record TenantCreateRequest(String tenantCode, String tenantName, String contactName, String contactPhone,
                                  String contactEmail, LocalDateTime expireAt, String isolationStrategy,
                                  String remark) {
}
