package cn.slainc.modules.upms.dto;

import java.util.List;

public record RolePermissionRequest(List<Long> permissionIds) {
}
