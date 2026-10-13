package cn.slainc.workorder.spi;

import cn.slainc.workorder.notify.WorkOrderNotification;
import java.util.List;


public interface WorkOrderNotificationStore {

    WorkOrderNotification save(WorkOrderNotification notification);

    List<WorkOrderNotification> findByRecipient(Long recipientId, boolean onlyUnread);

    void markRead(Long notificationId);

    void markAllRead(Long recipientId);

    long countUnread(Long recipientId);
}
