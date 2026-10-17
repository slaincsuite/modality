package cn.slainc.modules.policy.entity;

public record PolicyCustomTemplateRecord(String id, String name, String description, String document,
                                         String creator, long createTime, long updateTime) {
}
