package cn.slainc.modules.open.dto;

public record ScopeRequest(String name, String title, String description, String group, String riskLevel,
                           Boolean requiresAudit) {
}
