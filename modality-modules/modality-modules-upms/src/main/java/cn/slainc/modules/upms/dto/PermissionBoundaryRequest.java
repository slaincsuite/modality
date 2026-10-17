package cn.slainc.modules.upms.dto;

import java.util.List;

public record PermissionBoundaryRequest(List<String> allowedPermissions, List<String> deniedPermissions) {
}
