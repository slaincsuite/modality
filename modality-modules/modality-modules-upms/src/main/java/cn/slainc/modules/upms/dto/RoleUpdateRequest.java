package cn.slainc.modules.upms.dto;

public record RoleUpdateRequest(String name, Long parentId, String dataScope, Integer sort, String status,
                                String tags, String remark) {
}
