package cn.slainc.modules.policy.dto;

public record PolicyCreateRequest(String name, String subjectType, String subjectId, String loginType,
                                  String description, String group, String documentJson) {
}
