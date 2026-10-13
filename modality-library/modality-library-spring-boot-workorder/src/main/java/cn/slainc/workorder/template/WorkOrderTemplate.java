package cn.slainc.workorder.template;

import java.util.List;


public record WorkOrderTemplate(String code, String name, String typeCode, int version, boolean enabled,
                                List<WorkOrderFieldDefinition> fields) {

    public WorkOrderTemplate {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public WorkOrderTemplate withVersion(int newVersion) {
        return new WorkOrderTemplate(code, name, typeCode, newVersion, enabled, fields);
    }

    public WorkOrderTemplate disable() {
        return new WorkOrderTemplate(code, name, typeCode, version, false, fields);
    }
}
