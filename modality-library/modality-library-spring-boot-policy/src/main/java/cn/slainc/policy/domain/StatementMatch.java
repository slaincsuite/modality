package cn.slainc.policy.domain;

import java.util.List;

public record StatementMatch(String policyId, String policyName, String sid, Effect effect, String actionPattern,
                             String resourcePattern, List<ConditionEvaluation> conditions, boolean matched) {
}
