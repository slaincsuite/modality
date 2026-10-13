package cn.slainc.workorder.subscribe;

import cn.slainc.workorder.spi.WorkOrderStore;
import cn.slainc.workorder.spi.WorkOrderSubscriptionStore;
import java.util.List;


public class WorkOrderSubscriptionService {

    private final WorkOrderSubscriptionStore subscriptionStore;

    private final WorkOrderStore orderStore;

    public WorkOrderSubscriptionService(WorkOrderSubscriptionStore subscriptionStore, WorkOrderStore orderStore) {
        this.subscriptionStore = subscriptionStore;
        this.orderStore = orderStore;
    }

    public void subscribeOrder(Long orderId, Long subscriberId) {
        orderStore.findById(orderId).orElseThrow(() -> new cn.slainc.core.exception.BizException("工单不存在"));
        if (!subscriptionStore.existsByOrderAndSubscriber(orderId, subscriberId)) {
            WorkOrderSubscription subscription = new WorkOrderSubscription();
            subscription.setOrderId(orderId);
            subscription.setSubscriberId(subscriberId);
            subscription.setCreateTime(System.currentTimeMillis());
            subscriptionStore.save(subscription);
        }
    }

    public boolean unsubscribeOrder(Long orderId, Long subscriberId) {
        return subscriptionStore.deleteByOrderAndSubscriber(orderId, subscriberId);
    }

    public List<Long> orderSubscribers(Long orderId) {
        return subscriptionStore.findByOrder(orderId).stream()
                .map(WorkOrderSubscription::getSubscriberId)
                .toList();
    }

    public void subscribeType(String typeCode, Long subscriberId) {
        if (!subscriptionStore.existsByTypeAndSubscriber(typeCode, subscriberId)) {
            WorkOrderSubscription subscription = new WorkOrderSubscription();
            subscription.setTypeCode(typeCode);
            subscription.setSubscriberId(subscriberId);
            subscription.setCreateTime(System.currentTimeMillis());
            subscriptionStore.save(subscription);
        }
    }

    public void unsubscribeType(String typeCode, Long subscriberId) {
        subscriptionStore.deleteByTypeAndSubscriber(typeCode, subscriberId);
    }

    public List<String> myTypeSubscriptions(Long subscriberId) {
        return subscriptionStore.findTypeSubscriptionsBySubscriber(subscriberId).stream()
                .map(WorkOrderSubscription::getTypeCode)
                .distinct()
                .toList();
    }
}
