package cn.slainc.modules.policy.dto;

public record PolicyBoundaryView(String policyId, String document, String operator, long createTime,
                                 long updateTime) {
}
