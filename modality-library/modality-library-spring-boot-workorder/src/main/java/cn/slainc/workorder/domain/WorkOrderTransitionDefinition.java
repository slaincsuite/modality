package cn.slainc.workorder.domain;


public record WorkOrderTransitionDefinition(String source, String target, String action, String actionName,
                                            String conditionSpEL, String requiredPermission, int sort) {

    public static WorkOrderTransitionDefinition of(String source, String target, String action, String actionName) {
        return new WorkOrderTransitionDefinition(source, target, action, actionName, null, null, 0);
    }
}
