package cn.slainc.workorder.sla;

import cn.slainc.workorder.calendar.WorkCalendarCalculator;
import cn.slainc.workorder.calendar.WorkCalendarDefinition;
import java.time.Instant;


public class WorkOrderSlaCalculator {

    private final WorkCalendarDefinition calendar;

    public WorkOrderSlaCalculator(WorkCalendarDefinition calendar) {
        this.calendar = calendar;
    }

    public SlaSnapshot snapshot(SlaPolicyDefinition policy, Instant createTime, Instant firstResponseAt,
                                Instant resolvedAt, Instant now) {
        WorkCalendarCalculator calculator = new WorkCalendarCalculator(calendar);
        Instant effectiveEnd = resolvedAt != null ? resolvedAt : now;
        long elapsed = calculator.workingMillisBetween(createTime, effectiveEnd);
        long responseRemaining = firstResponseAt != null
                ? 0
                : Math.max(0, policy.responseMillis() - elapsed);
        long resolveRemaining = resolvedAt != null
                ? 0
                : Math.max(0, policy.resolveMillis() - elapsed);
        SlaState responseState = stateFor(policy, elapsed, firstResponseAt != null, policy.responseMillis());
        SlaState resolveState = stateFor(policy, elapsed, resolvedAt != null, policy.resolveMillis());
        Instant resolveDeadline = calculator.addWorkingMillis(createTime, policy.resolveMillis());
        return new SlaSnapshot(policy.code(), elapsed, responseRemaining, resolveRemaining,
                responseState, resolveState, resolveDeadline);
    }

    private SlaState stateFor(SlaPolicyDefinition policy, long elapsed, boolean done, long threshold) {
        if (done) {
            return SlaState.ON_TRACK;
        }
        if (elapsed >= threshold) {
            return SlaState.BREACHED;
        }
        long warnThreshold = threshold * policy.warnPercent() / 100;
        if (elapsed >= warnThreshold) {
            return SlaState.WARNING;
        }
        return SlaState.ON_TRACK;
    }
}
