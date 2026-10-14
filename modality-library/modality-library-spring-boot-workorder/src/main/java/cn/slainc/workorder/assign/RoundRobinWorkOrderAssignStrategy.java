package cn.slainc.workorder.assign;

import cn.slainc.workorder.domain.WorkOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


public class RoundRobinWorkOrderAssignStrategy implements WorkOrderAssignStrategy {

    private final AtomicLong cursor = new AtomicLong();

    @Override
    public String name() {
        return "round-robin";
    }

    @Override
    public Long pick(WorkOrder order, List<Long> candidates, java.util.function.ToLongFunction<Long> activeCount) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<Long> sorted = candidates.stream().sorted().toList();
        int index = (int) (Math.floorMod(cursor.getAndIncrement(), sorted.size()));
        return sorted.get(index);
    }
}
