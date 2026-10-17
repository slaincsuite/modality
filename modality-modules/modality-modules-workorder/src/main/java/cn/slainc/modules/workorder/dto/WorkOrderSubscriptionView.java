package cn.slainc.modules.workorder.dto;

import cn.slainc.workorder.subscribe.WorkOrderSubscription;


public record WorkOrderSubscriptionView(boolean subscribed, Long subscriptionId, Long orderId,
                                        Long subscriberId, Long createTime) {

    public static WorkOrderSubscriptionView of(WorkOrderSubscription subscription) {
        if (subscription == null) {
            return new WorkOrderSubscriptionView(false, null, null, null, null);
        }
        return new WorkOrderSubscriptionView(true, subscription.getId(), subscription.getOrderId(),
                subscription.getSubscriberId(), subscription.getCreateTime());
    }
}
