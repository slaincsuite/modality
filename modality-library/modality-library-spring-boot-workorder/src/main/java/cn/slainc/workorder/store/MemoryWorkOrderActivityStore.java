package cn.slainc.workorder.store;

import cn.slainc.workorder.domain.WorkOrderActivity;
import cn.slainc.workorder.spi.WorkOrderActivityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class MemoryWorkOrderActivityStore implements WorkOrderActivityStore {

    private final Map<Long, List<WorkOrderActivity>> activities = new ConcurrentHashMap<>();

    @Override
    public void save(WorkOrderActivity activity) {
        activities.computeIfAbsent(activity.orderId(), k -> new ArrayList<>()).add(activity);
    }

    @Override
    public List<WorkOrderActivity> findByOrder(Long orderId) {
        return List.copyOf(activities.getOrDefault(orderId, List.of()));
    }
}
