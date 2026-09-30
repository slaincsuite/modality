package cn.slainc.modules.policy.dto;

public record PolicyTestCaseRequest(String policyId, String name, String description, String requestJson,
                                    String expected, boolean enabled) {
}
