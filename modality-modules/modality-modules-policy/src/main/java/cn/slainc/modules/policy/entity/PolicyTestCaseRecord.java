package cn.slainc.modules.policy.entity;

public record PolicyTestCaseRecord(String id, String policyId, String name, String description,
                                   String requestJson, String expected, boolean enabled,
                                   long createTime, long updateTime) {
}
