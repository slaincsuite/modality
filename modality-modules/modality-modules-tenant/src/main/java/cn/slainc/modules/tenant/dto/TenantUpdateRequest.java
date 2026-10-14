package cn.slainc.modules.tenant.dto;

public record TenantUpdateRequest(String tenantName, String contactName, String contactPhone, String contactEmail,
                                  String remark) {
}
