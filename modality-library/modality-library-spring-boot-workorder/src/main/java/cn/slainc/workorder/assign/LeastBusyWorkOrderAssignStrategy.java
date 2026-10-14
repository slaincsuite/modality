package cn.slainc.workorder.assign;

import cn.slainc.workorder.domain.WorkOrder;
import java.util.Comparator;
import java.util.List;


public class LeastBusyWorkOrderAssignStrategy implements WorkOrderAssignStrategy {

    @Override
    public String name() {
        return "least-busy";
    }

    @Override
    public Long pick(WorkOrder order, List<Long> candidates, java.util.function.ToLongFunction<Long> activeCount) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .min(Comparator.comparingLong(activeCount))
                .orElse(null);
    }
}
