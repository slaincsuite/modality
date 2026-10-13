package cn.slainc.workorder.sla;

import java.time.Instant;


public record SlaSnapshot(String policyCode, long elapsedWorkingMillis, long responseRemainingMillis,
                          long resolveRemainingMillis, SlaState responseState, SlaState resolveState,
                          Instant resolveDeadline) {
}
