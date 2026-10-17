package cn.slainc.modules.upms.dto;

import java.util.List;

public record PermissionBoundaryView(Long userId, List<String> allowedPermissions,
                                     List<String> deniedPermissions, Long updateTime) {
}
