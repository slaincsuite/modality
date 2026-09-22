package cn.slainc.modules.upms.dto;

public record MenuRequest(String name, Long parentId, String type, String path, String component, String icon,
                          String permissionCode, Boolean visible, Integer sort, String status) {
}
