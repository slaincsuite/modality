package cn.slainc.workorder.domain;

import java.util.List;


public final class WorkOrderStateMachines {

    public static final String DEFAULT_TYPE = "default";

    public static List<WorkOrderStateDefinition> defaultStates() {
        return List.of(
                WorkOrderStateDefinition.initial("OPEN", "待处理"),
                WorkOrderStateDefinition.intermediate("IN_PROGRESS", "处理中", 1),
                WorkOrderStateDefinition.intermediate("SUSPENDED", "已挂起", 2),
                WorkOrderStateDefinition.intermediate("RESOLVED", "已解决", 3),
                WorkOrderStateDefinition.terminal("CLOSED", "已关闭", 4));
    }

    public static List<WorkOrderTransitionDefinition> defaultTransitions() {
        return List.of(
                WorkOrderTransitionDefinition.of("OPEN", "IN_PROGRESS", "assign", "受理"),
                WorkOrderTransitionDefinition.of("OPEN", "IN_PROGRESS", "claim", "认领"),
                WorkOrderTransitionDefinition.of("IN_PROGRESS", "IN_PROGRESS", "transfer", "转派"),
                WorkOrderTransitionDefinition.of("IN_PROGRESS", "IN_PROGRESS", "escalate", "升级"),
                WorkOrderTransitionDefinition.of("IN_PROGRESS", "SUSPENDED", "suspend", "挂起"),
                WorkOrderTransitionDefinition.of("SUSPENDED", "IN_PROGRESS", "resume", "恢复"),
                WorkOrderTransitionDefinition.of("IN_PROGRESS", "RESOLVED", "resolve", "标记解决"),
                WorkOrderTransitionDefinition.of("OPEN", "CLOSED", "close", "关闭"),
                WorkOrderTransitionDefinition.of("IN_PROGRESS", "CLOSED", "close", "关闭"),
                WorkOrderTransitionDefinition.of("SUSPENDED", "CLOSED", "close", "关闭"),
                WorkOrderTransitionDefinition.of("RESOLVED", "CLOSED", "close", "关闭"),
                WorkOrderTransitionDefinition.of("CLOSED", "OPEN", "reopen", "重开"));
    }

    private WorkOrderStateMachines() {
    }
}
