package cn.slainc.modules.policy.dto;

public record TemplateImportRequest(String template, String name, String subjectType, String subjectId,
                                    String group) {
}
