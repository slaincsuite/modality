package cn.slainc.workorder.spi;

import cn.slainc.workorder.subscribe.WorkOrderSubscription;
import java.util.List;


public interface WorkOrderSubscriptionStore {

    void save(WorkOrderSubscription subscription);

    boolean deleteByOrderAndSubscriber(Long orderId, Long subscriberId);

    boolean existsByOrderAndSubscriber(Long orderId, Long subscriberId);

    List<WorkOrderSubscription> findByOrder(Long orderId);

    List<WorkOrderSubscription> findBySubscriber(Long subscriberId);

    boolean existsByTypeAndSubscriber(String typeCode, Long subscriberId);

    void deleteByTypeAndSubscriber(String typeCode, Long subscriberId);

    List<WorkOrderSubscription> findTypeSubscriptionsBySubscriber(Long subscriberId);
}
