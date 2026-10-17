package cn.slainc.modules.workorder.service.order;

import cn.slainc.modules.workorder.dto.WorkOrderTypeView;
import cn.slainc.workorder.type.WorkOrderTypeRegistry;
import java.util.List;


public class WorkOrderTypeService {

    private final WorkOrderTypeRegistry typeRegistry;

    public WorkOrderTypeService(WorkOrderTypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
    }

    public List<WorkOrderTypeView> list() {
        return typeRegistry.all().stream()
                .map(type -> new WorkOrderTypeView(type.code(), type.name(), type.description(),
                        type.enabled()))
                .toList();
    }
}
