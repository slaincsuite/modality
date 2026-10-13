package cn.slainc.workorder.engine;

import cn.slainc.workorder.model.WorkOrderTransitionDefinition;


public record WorkOrderTransitionPreview(WorkOrderTransitionDefinition definition, boolean allowed) {
}
