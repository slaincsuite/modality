package cn.slainc.policy.storage;

public record PolicyBindingOrigin(Long bindingId, String policyId, String sourcePolicyId,
                                  String subjectType, String subjectId, Long tenantId, long createTime) {
}
