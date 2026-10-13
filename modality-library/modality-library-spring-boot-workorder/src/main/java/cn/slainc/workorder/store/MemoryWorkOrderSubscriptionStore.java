package cn.slainc.workorder.store;

import cn.slainc.workorder.subscribe.WorkOrderSubscription;
import cn.slainc.workorder.spi.WorkOrderSubscriptionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class MemoryWorkOrderSubscriptionStore implements WorkOrderSubscriptionStore {

    private final Map<Long, WorkOrderSubscription> subscriptions = new ConcurrentHashMap<>();

    private final AtomicLong ids = new AtomicLong();

    @Override
    public void save(WorkOrderSubscription subscription) {
        if (subscription.getId() == null) {
            subscription.setId(ids.incrementAndGet());
        }
        subscriptions.put(subscription.getId(), subscription);
    }

    @Override
    public boolean deleteByOrderAndSubscriber(Long orderId, Long subscriberId) {
        return subscriptions.values().removeIf(s -> s.getOrderId() != null
                && s.getOrderId().equals(orderId) && s.getSubscriberId().equals(subscriberId));
    }

    @Override
    public boolean existsByOrderAndSubscriber(Long orderId, Long subscriberId) {
        return subscriptions.values().stream().anyMatch(s -> s.getOrderId() != null
                && s.getOrderId().equals(orderId) && s.getSubscriberId().equals(subscriberId));
    }

    @Override
    public List<WorkOrderSubscription> findByOrder(Long orderId) {
        return subscriptions.values().stream()
                .filter(s -> s.getOrderId() != null && s.getOrderId().equals(orderId))
                .toList();
    }

    @Override
    public List<WorkOrderSubscription> findBySubscriber(Long subscriberId) {
        return subscriptions.values().stream()
                .filter(s -> s.getSubscriberId().equals(subscriberId))
                .toList();
    }

    @Override
    public boolean existsByTypeAndSubscriber(String typeCode, Long subscriberId) {
        return subscriptions.values().stream().anyMatch(s -> typeCode.equals(s.getTypeCode())
                && s.getSubscriberId().equals(subscriberId));
    }

    @Override
    public void deleteByTypeAndSubscriber(String typeCode, Long subscriberId) {
        subscriptions.values().removeIf(s -> typeCode.equals(s.getTypeCode())
                && s.getSubscriberId().equals(subscriberId));
    }

    @Override
    public List<WorkOrderSubscription> findTypeSubscriptionsBySubscriber(Long subscriberId) {
        return subscriptions.values().stream()
                .filter(s -> s.getTypeCode() != null && s.getSubscriberId().equals(subscriberId))
                .toList();
    }
}
