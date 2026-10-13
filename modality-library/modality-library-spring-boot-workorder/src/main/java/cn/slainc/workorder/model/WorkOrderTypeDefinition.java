package cn.slainc.workorder.model;

import java.util.List;


public record WorkOrderTypeDefinition(String code, String name, String description, String icon, String color,
                                      String category, int sortOrder, boolean enabled,
                                      List<WorkOrderStateDefinition> states,
                                      List<WorkOrderTransitionDefinition> transitions) {

    public static WorkOrderTypeDefinition generic() {
        return new WorkOrderTypeDefinition("generic", "通用工单", "通用事务请求", null, null, "general", 0, true,
                WorkOrderStateMachines.defaultStates(), WorkOrderStateMachines.defaultTransitions());
    }

    public static WorkOrderTypeDefinition approval() {
        return new WorkOrderTypeDefinition("approval", "审批工单", "审批类请求", null, null, "approval", 1, true,
                WorkOrderStateMachines.defaultStates(), WorkOrderStateMachines.defaultTransitions());
    }

    public static WorkOrderTypeDefinition fault() {
        return new WorkOrderTypeDefinition("fault", "故障工单", "故障申报与处理", null, null, "fault", 2, true,
                WorkOrderStateMachines.defaultStates(), WorkOrderStateMachines.defaultTransitions());
    }
}
