package cn.slainc.workorder.template;

import java.util.List;


public record WorkOrderFieldDefinition(String code, String name, WorkOrderFieldType type, boolean required,
                                       String placeholder, String defaultValue, Integer minLength, Integer maxLength,
                                       Number minValue, Number maxValue, String pattern, List<String> options,
                                       boolean readonly, int sort) {

    public WorkOrderFieldDefinition {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static WorkOrderFieldDefinition requiredText(String code, String name, int maxLength) {
        return new WorkOrderFieldDefinition(code, name, WorkOrderFieldType.TEXT, true, null, null,
                null, maxLength, null, null, null, null, false, 0);
    }

    public static WorkOrderFieldDefinition optional(WorkOrderFieldType type, String code, String name) {
        return new WorkOrderFieldDefinition(code, name, type, false, null, null,
                null, null, null, null, null, null, false, 0);
    }
}
