package cn.slainc.workorder.assign;

import cn.slainc.workorder.model.WorkOrder;
import java.util.List;


public interface WorkOrderAssignStrategy {

    String name();

    Long pick(WorkOrder order, List<Long> candidates, java.util.function.ToLongFunction<Long> activeCount);
}
