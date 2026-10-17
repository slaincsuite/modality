package cn.slainc.modules.upms.dto;

import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.PolicyBindingRecord;

import java.util.List;

public record RolePermissionSourceView(List<PermissionRecord> directPermissions,
                                       List<PolicyBindingRecord> policyBindings) {
}
