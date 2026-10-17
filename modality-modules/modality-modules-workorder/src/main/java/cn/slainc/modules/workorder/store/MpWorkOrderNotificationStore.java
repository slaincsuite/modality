package cn.slainc.modules.workorder.store;

import cn.slainc.modules.workorder.entity.WorkOrderNotificationRecord;
import cn.slainc.modules.workorder.mapper.WorkOrderNotificationMapper;
import cn.slainc.workorder.notify.WorkOrderNotification;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Comparator;
import java.util.List;


public class MpWorkOrderNotificationStore implements WorkOrderNotificationStore {

    private final WorkOrderNotificationMapper notificationMapper;

    public MpWorkOrderNotificationStore(WorkOrderNotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public WorkOrderNotification save(WorkOrderNotification notification) {
        WorkOrderNotificationRecord record = toRecord(notification);
        if (notification.getId() == null) {
            notificationMapper.insert(record);
        } else {
            notificationMapper.updateById(record);
        }
        notification.setId(record.getId());
        return notification;
    }

    @Override
    public List<WorkOrderNotification> findByRecipient(Long recipientId, boolean onlyUnread) {
        return notificationMapper.selectList(new LambdaQueryWrapper<WorkOrderNotificationRecord>()
                        .eq(WorkOrderNotificationRecord::getRecipientId, recipientId)
                        .eq(onlyUnread, WorkOrderNotificationRecord::getReadFlag, false)
                        .orderByDesc(WorkOrderNotificationRecord::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void markRead(Long notificationId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<WorkOrderNotificationRecord>()
                .eq(WorkOrderNotificationRecord::getId, notificationId)
                .set(WorkOrderNotificationRecord::getReadFlag, true));
    }

    @Override
    public boolean markRead(Long notificationId, Long recipientId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<WorkOrderNotificationRecord>()
                .eq(WorkOrderNotificationRecord::getId, notificationId)
                .eq(WorkOrderNotificationRecord::getRecipientId, recipientId));
        if (count == null || count == 0) {
            return false;
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<WorkOrderNotificationRecord>()
                .eq(WorkOrderNotificationRecord::getId, notificationId)
                .eq(WorkOrderNotificationRecord::getRecipientId, recipientId)
                .set(WorkOrderNotificationRecord::getReadFlag, true));
        return true;
    }

    @Override
    public void markAllRead(Long recipientId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<WorkOrderNotificationRecord>()
                .eq(WorkOrderNotificationRecord::getRecipientId, recipientId)
                .set(WorkOrderNotificationRecord::getReadFlag, true));
    }

    @Override
    public long countUnread(Long recipientId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<WorkOrderNotificationRecord>()
                .eq(WorkOrderNotificationRecord::getRecipientId, recipientId)
                .eq(WorkOrderNotificationRecord::getReadFlag, false));
    }

    private WorkOrderNotificationRecord toRecord(WorkOrderNotification notification) {
        WorkOrderNotificationRecord record = new WorkOrderNotificationRecord();
        record.setId(notification.getId());
        record.setOrderId(notification.getOrderId());
        record.setNumber(notification.getNumber());
        record.setRecipientId(notification.getRecipientId());
        record.setEventCode(notification.getEventCode());
        record.setTitle(notification.getTitle());
        record.setContent(notification.getContent());
        record.setChannel(notification.getChannel());
        record.setReadFlag(notification.isRead());
        record.setCreateTime(notification.getCreateTime());
        return record;
    }

    private WorkOrderNotification toDomain(WorkOrderNotificationRecord record) {
        WorkOrderNotification notification = new WorkOrderNotification();
        notification.setId(record.getId());
        notification.setOrderId(record.getOrderId());
        notification.setNumber(record.getNumber());
        notification.setRecipientId(record.getRecipientId());
        notification.setEventCode(record.getEventCode());
        notification.setTitle(record.getTitle());
        notification.setContent(record.getContent());
        notification.setChannel(record.getChannel());
        notification.setRead(record.getReadFlag() != null && record.getReadFlag());
        notification.setCreateTime(record.getCreateTime());
        return notification;
    }
}
