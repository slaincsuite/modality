package cn.slainc.modules.policy.dto;

import cn.slainc.policy.domain.PolicyBinding;

public record PolicyBindingView(Long id, Long tenantId, String policyId, String sourcePolicyId,
                                String subjectType, String subjectId, String operator, Long createTime) {

    public static PolicyBindingView of(PolicyBinding binding) {
        return new PolicyBindingView(binding.getId(), binding.getTenantId(), binding.getPolicyId(),
                binding.getSourcePolicyId(), binding.getSubjectType(), binding.getSubjectId(),
                binding.getOperator(), binding.getCreateTime());
    }
}
