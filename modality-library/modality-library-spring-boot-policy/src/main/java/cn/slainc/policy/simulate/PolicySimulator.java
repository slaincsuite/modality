package cn.slainc.policy.simulate;

import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.domain.ConditionEvaluation;
import cn.slainc.policy.domain.PolicyDecision;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.domain.StatementMatch;

import java.util.ArrayList;
import java.util.List;

public class PolicySimulator {

    private final PolicyEngine engine;

    public PolicySimulator(PolicyEngine engine) {
        this.engine = engine;
    }

    public PolicyDecision simulate(PolicyRequest request) {
        return engine.evaluate(request);
    }

    public List<String> explain(PolicyRequest request) {
        return explain(simulate(request));
    }

    public List<String> explain(PolicyDecision decision) {
        List<String> lines = new ArrayList<>();
        lines.add("决策结果: " + decisionText(decision.decision()));
        lines.add("原因: " + decision.reason());
        if (decision.matches().isEmpty()) {
            lines.add("没有任何语句命中 action 与 resource");
            return lines;
        }
        int index = 0;
        for (StatementMatch match : decision.matches()) {
            index++;
            String status = match.matched() ? "生效" : "条件未通过";
            lines.add("语句" + index + " [" + status + "] " + match.policyId()
                    + (match.sid() == null ? "" : "/" + match.sid())
                    + "，效果 " + match.effect().code()
                    + "，" + match.actionPattern() + " → " + match.resourcePattern());
            for (ConditionEvaluation evaluation : match.conditions()) {
                lines.add("  条件 " + evaluation.operator() + " " + evaluation.key()
                        + "，期望 " + evaluation.expected()
                        + "，实际 " + evaluation.actual()
                        + "，" + (evaluation.passed() ? "通过" : "未通过"));
            }
        }
        return lines;
    }

    public PolicyTestReport runCases(List<PolicyTestCase> testCases) {
        List<PolicyTestReport.CaseResult> results = new ArrayList<>();
        int passed = 0;
        for (PolicyTestCase testCase : testCases) {
            PolicyDecision decision = simulate(testCase.request());
            boolean success = decision.decision() == testCase.expected();
            if (success) {
                passed++;
            }
            results.add(new PolicyTestReport.CaseResult(testCase.name(), success, testCase.expected(),
                    decision.decision(), decision.reason()));
        }
        return new PolicyTestReport(testCases.size(), passed, results);
    }

    private String decisionText(PolicyDecision.Decision decision) {
        return switch (decision) {
            case ALLOW -> "允许";
            case DENY -> "拒绝";
            case NOT_APPLICABLE -> "未命中（默认拒绝）";
        };
    }
}
