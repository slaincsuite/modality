package cn.slainc.policy.model;

import java.util.List;

public record PolicyDecision(Decision decision, List<StatementMatch> matches, String reason) {

    public enum Decision {
        ALLOW,
        DENY,
        NOT_APPLICABLE
    }

    public static PolicyDecision allow(List<StatementMatch> matches, String reason) {
        return new PolicyDecision(Decision.ALLOW, List.copyOf(matches), reason);
    }

    public static PolicyDecision deny(List<StatementMatch> matches, String reason) {
        return new PolicyDecision(Decision.DENY, List.copyOf(matches), reason);
    }

    public static PolicyDecision notApplicable(String reason) {
        return new PolicyDecision(Decision.NOT_APPLICABLE, List.of(), reason);
    }

    public boolean allowed() {
        return decision == Decision.ALLOW;
    }
}
