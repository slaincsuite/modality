package cn.slainc.modules.upms.dto;

public record RoleCreateRequest(String code, String name, Long parentId, String dataScope, Integer sort,
                                String tags, String remark) {
}
