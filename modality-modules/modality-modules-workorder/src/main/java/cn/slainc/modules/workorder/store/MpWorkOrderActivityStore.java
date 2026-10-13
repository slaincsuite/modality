package cn.slainc.modules.workorder.store;

import cn.slainc.modules.workorder.entity.WorkOrderActivityRecord;
import cn.slainc.modules.workorder.mapper.WorkOrderActivityMapper;
import cn.slainc.workorder.model.WorkOrderActivity;
import cn.slainc.workorder.spi.WorkOrderActivityStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;


public class MpWorkOrderActivityStore implements WorkOrderActivityStore {

    private final WorkOrderActivityMapper activityMapper;

    public MpWorkOrderActivityStore(WorkOrderActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    @Override
    public void save(WorkOrderActivity activity) {
        WorkOrderActivityRecord record = new WorkOrderActivityRecord();
        record.setOrderId(activity.orderId());
        record.setAction(activity.action());
        record.setOperatorId(activity.operatorId());
        record.setTargetUserId(activity.targetUserId());
        record.setReason(activity.reason());
        record.setCreateTime(activity.createTime());
        activityMapper.insert(record);
    }

    @Override
    public List<WorkOrderActivity> findByOrder(Long orderId) {
        return activityMapper.selectList(new LambdaQueryWrapper<WorkOrderActivityRecord>()
                        .eq(WorkOrderActivityRecord::getOrderId, orderId)
                        .orderByAsc(WorkOrderActivityRecord::getCreateTime))
                .stream()
                .map(record -> new WorkOrderActivity(record.getOrderId(), record.getAction(),
                        record.getOperatorId(), record.getTargetUserId(), record.getReason(),
                        record.getCreateTime()))
                .toList();
    }
}
