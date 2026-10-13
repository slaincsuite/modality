package cn.slainc.modules.workorder.store;

import cn.slainc.modules.workorder.entity.WorkOrderSubscriptionRecord;
import cn.slainc.modules.workorder.mapper.WorkOrderSubscriptionMapper;
import cn.slainc.workorder.subscribe.WorkOrderSubscription;
import cn.slainc.workorder.spi.WorkOrderSubscriptionStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;


public class MpWorkOrderSubscriptionStore implements WorkOrderSubscriptionStore {

    private final WorkOrderSubscriptionMapper subscriptionMapper;

    public MpWorkOrderSubscriptionStore(WorkOrderSubscriptionMapper subscriptionMapper) {
        this.subscriptionMapper = subscriptionMapper;
    }

    @Override
    public void save(WorkOrderSubscription subscription) {
        if (subscription.getId() == null) {
            subscriptionMapper.insert(toRecord(subscription));
        } else {
            subscriptionMapper.updateById(toRecord(subscription));
        }
        subscription.setId(subscription.getId());
    }

    @Override
    public boolean deleteByOrderAndSubscriber(Long orderId, Long subscriberId) {
        return subscriptionMapper.delete(new LambdaQueryWrapper<WorkOrderSubscriptionRecord>()
                .eq(WorkOrderSubscriptionRecord::getOrderId, orderId)
                .eq(WorkOrderSubscriptionRecord::getSubscriberId, subscriberId)) > 0;
    }

    @Override
    public boolean existsByOrderAndSubscriber(Long orderId, Long subscriberId) {
        return subscriptionMapper.selectCount(new LambdaQueryWrapper<WorkOrderSubscriptionRecord>()
                .eq(WorkOrderSubscriptionRecord::getOrderId, orderId)
                .eq(WorkOrderSubscriptionRecord::getSubscriberId, subscriberId)) > 0;
    }

    @Override
    public List<WorkOrderSubscription> findByOrder(Long orderId) {
        return subscriptionMapper.selectList(new LambdaQueryWrapper<WorkOrderSubscriptionRecord>()
                .eq(WorkOrderSubscriptionRecord::getOrderId, orderId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<WorkOrderSubscription> findBySubscriber(Long subscriberId) {
        return subscriptionMapper.selectList(new LambdaQueryWrapper<WorkOrderSubscriptionRecord>()
                .eq(WorkOrderSubscriptionRecord::getSubscriberId, subscriberId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByTypeAndSubscriber(String typeCode, Long subscriberId) {
        return subscriptionMapper.selectCount(new LambdaQueryWrapper<WorkOrderSubscriptionRecord>()
                .eq(WorkOrderSubscriptionRecord::getTypeCode, typeCode)
                .eq(WorkOrderSubscriptionRecord::getSubscriberId, subscriberId)) > 0;
    }

    @Override
    public void deleteByTypeAndSubscriber(String typeCode, Long subscriberId) {
        subscriptionMapper.delete(new LambdaQueryWrapper<WorkOrderSubscriptionRecord>()
                .eq(WorkOrderSubscriptionRecord::getTypeCode, typeCode)
                .eq(WorkOrderSubscriptionRecord::getSubscriberId, subscriberId));
    }

    @Override
    public List<WorkOrderSubscription> findTypeSubscriptionsBySubscriber(Long subscriberId) {
        return subscriptionMapper.selectList(new LambdaQueryWrapper<WorkOrderSubscriptionRecord>()
                .isNotNull(WorkOrderSubscriptionRecord::getTypeCode)
                .eq(WorkOrderSubscriptionRecord::getSubscriberId, subscriberId))
                .stream().map(this::toDomain).toList();
    }

    private WorkOrderSubscriptionRecord toRecord(WorkOrderSubscription subscription) {
        WorkOrderSubscriptionRecord record = new WorkOrderSubscriptionRecord();
        record.setId(subscription.getId());
        record.setOrderId(subscription.getOrderId());
        record.setTypeCode(subscription.getTypeCode());
        record.setSubscriberId(subscription.getSubscriberId());
        record.setCreateTime(subscription.getCreateTime());
        return record;
    }

    private WorkOrderSubscription toDomain(WorkOrderSubscriptionRecord record) {
        WorkOrderSubscription subscription = new WorkOrderSubscription();
        subscription.setId(record.getId());
        subscription.setOrderId(record.getOrderId());
        subscription.setTypeCode(record.getTypeCode());
        subscription.setSubscriberId(record.getSubscriberId());
        subscription.setCreateTime(record.getCreateTime());
        return subscription;
    }
}
