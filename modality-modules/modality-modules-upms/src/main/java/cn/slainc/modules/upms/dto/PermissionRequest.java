package cn.slainc.modules.upms.dto;

public record PermissionRequest(String code, String name, String type, Long parentId, Integer sort, String remark,
                                String status) {
}
