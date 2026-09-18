package cn.slainc.policy.simulate;

import cn.slainc.policy.model.PolicyDecision;

import java.util.ArrayList;
import java.util.List;

public record PolicyTestReport(int total, int passed, List<CaseResult> results) {

    public record CaseResult(String name, boolean passed, PolicyDecision.Decision expected,
                             PolicyDecision.Decision actual, String reason) {
    }

    public List<String> summary() {
        List<String> lines = new ArrayList<>();
        lines.add("共 " + total + " 个用例，通过 " + passed + " 个，失败 " + (total - passed) + " 个");
        for (CaseResult result : results) {
            lines.add((result.passed() ? "[通过] " : "[失败] ") + result.name()
                    + "，期望 " + decisionText(result.expected()) + "，实际 " + decisionText(result.actual())
                    + (result.reason() == null || result.reason().isBlank() ? "" : "，" + result.reason()));
        }
        return lines;
    }

    private String decisionText(PolicyDecision.Decision decision) {
        return switch (decision) {
            case ALLOW -> "允许";
            case DENY -> "拒绝";
            case NOT_APPLICABLE -> "未命中";
        };
    }
}
