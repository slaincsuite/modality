package cn.slainc.modules.upms.dto;

public record SystemRequest(String code, String name, String moduleCode, String visibility, String groupName,
                            String icon, String url, Integer sort, String status) {
}
