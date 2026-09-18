package cn.slainc.policy.simulate;

import cn.slainc.policy.model.PolicyDecision;
import cn.slainc.policy.model.PolicyRequest;

public record PolicyTestCase(String name, PolicyRequest request, PolicyDecision.Decision expected) {
}
