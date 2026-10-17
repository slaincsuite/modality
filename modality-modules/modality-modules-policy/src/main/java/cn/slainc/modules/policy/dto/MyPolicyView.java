package cn.slainc.modules.policy.dto;

import java.util.List;

public record MyPolicyView(List<PolicyView> directPolicies, List<PolicyView> rolePolicies,
                           List<PolicyView> groupPolicies, List<PolicyView> policies) {
}
