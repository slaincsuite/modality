package cn.slainc.workorder.store;

import cn.slainc.workorder.domain.WorkOrder;
import cn.slainc.workorder.spi.WorkOrderStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class MemoryWorkOrderStore implements WorkOrderStore {

    private final Map<Long, WorkOrder> orders = new ConcurrentHashMap<>();

    private final Map<String, Long> numberIndex = new ConcurrentHashMap<>();

    private final AtomicLong ids = new AtomicLong();

    @Override
    public WorkOrder save(WorkOrder order) {
        if (order.getId() == null) {
            order.setId(ids.incrementAndGet());
        }
        orders.put(order.getId(), order);
        if (order.getNumber() != null) {
            numberIndex.put(order.getNumber(), order.getId());
        }
        return order;
    }

    @Override
    public void update(WorkOrder order) {
        orders.put(order.getId(), order);
    }

    @Override
    public Optional<WorkOrder> findById(Long id) {
        WorkOrder order = orders.get(id);
        return order == null || order.isDeleted() ? Optional.empty() : Optional.of(order);
    }

    @Override
    public Optional<WorkOrder> findByNumber(String number) {
        Long id = numberIndex.get(number);
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public long countActiveByAssignee(Long assigneeId) {
        return orders.values().stream()
                .filter(order -> !order.isDeleted())
                .filter(order -> assigneeId.equals(order.getAssigneeId()))
                .filter(order -> !"CLOSED".equals(order.getStatus()) && !"RESOLVED".equals(order.getStatus()))
                .count();
    }
}
