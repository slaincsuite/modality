package cn.slainc.workorder.sla;


public record SlaPolicyDefinition(String code, String name, String typeCode, String priority,
                                  long responseMillis, long resolveMillis, int warnPercent) {

    public boolean matches(String orderTypeCode, String orderPriority) {
        boolean typeMatches = typeCode == null || typeCode.equals(orderTypeCode);
        boolean priorityMatches = priority == null || priority.equals(orderPriority);
        return typeMatches && priorityMatches;
    }
}
