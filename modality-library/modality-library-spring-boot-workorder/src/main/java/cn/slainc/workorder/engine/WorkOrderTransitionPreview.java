package cn.slainc.workorder.engine;

import cn.slainc.workorder.domain.WorkOrderTransitionDefinition;


public record WorkOrderTransitionPreview(WorkOrderTransitionDefinition definition, boolean allowed) {
}
