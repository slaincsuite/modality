package cn.slainc.modules.policy.dto;

import java.util.List;

public record PolicyGeneratorRequest(String name, String description, String subjectType, String subjectId,
                                     String loginType, String group, List<PolicyGeneratorRule> rules,
                                     Boolean includeDangerousDenyGuardrail) {
}
