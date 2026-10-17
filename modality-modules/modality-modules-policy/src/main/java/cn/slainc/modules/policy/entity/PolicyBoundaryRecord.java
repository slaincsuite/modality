package cn.slainc.modules.policy.entity;

public record PolicyBoundaryRecord(String policyId, String document, String operator,
                                   long createTime, long updateTime) {
}
