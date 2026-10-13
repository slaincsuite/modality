package cn.slainc.workorder.model;


public record WorkOrderStateDefinition(String code, String name, String color,
                                       WorkOrderStateKind kind, int sort) {

    public static WorkOrderStateDefinition initial(String code, String name) {
        return new WorkOrderStateDefinition(code, name, null, WorkOrderStateKind.INITIAL, 0);
    }

    public static WorkOrderStateDefinition intermediate(String code, String name, int sort) {
        return new WorkOrderStateDefinition(code, name, null, WorkOrderStateKind.INTERMEDIATE, sort);
    }

    public static WorkOrderStateDefinition terminal(String code, String name, int sort) {
        return new WorkOrderStateDefinition(code, name, null, WorkOrderStateKind.TERMINAL, sort);
    }
}
