package cn.slainc.policy.simulate;

import cn.slainc.policy.domain.PolicyDecision;
import cn.slainc.policy.domain.PolicyRequest;

public record PolicyTestCase(String name, PolicyRequest request, PolicyDecision.Decision expected) {
}
