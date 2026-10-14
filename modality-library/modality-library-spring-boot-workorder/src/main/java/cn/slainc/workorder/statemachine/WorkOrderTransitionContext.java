package cn.slainc.workorder.statemachine;

import cn.slainc.workorder.domain.WorkOrder;
import java.util.Collections;
import java.util.Map;


public record WorkOrderTransitionContext(WorkOrder order, Long operatorId, Map<String, Object> variables) {

    public WorkOrderTransitionContext {
        variables = variables == null ? Collections.emptyMap() : Collections.unmodifiableMap(variables);
    }

    public static WorkOrderTransitionContext of(WorkOrder order, Long operatorId) {
        return new WorkOrderTransitionContext(order, operatorId, Collections.emptyMap());
    }
}
