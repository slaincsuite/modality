package cn.slainc.modules.policy.dto;

public record PolicyCustomTemplateView(String id, String templateKey, String name, String description,
                                       String document, String creator, long createTime, long updateTime) {
}
