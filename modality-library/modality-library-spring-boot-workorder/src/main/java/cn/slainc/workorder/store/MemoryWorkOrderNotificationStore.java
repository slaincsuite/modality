package cn.slainc.workorder.store;

import cn.slainc.workorder.notify.WorkOrderNotification;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class MemoryWorkOrderNotificationStore implements WorkOrderNotificationStore {

    private final Map<Long, WorkOrderNotification> notifications = new ConcurrentHashMap<>();

    private final AtomicLong ids = new AtomicLong();

    @Override
    public WorkOrderNotification save(WorkOrderNotification notification) {
        if (notification.getId() == null) {
            notification.setId(ids.incrementAndGet());
        }
        notifications.put(notification.getId(), notification);
        return notification;
    }

    @Override
    public List<WorkOrderNotification> findByRecipient(Long recipientId, boolean onlyUnread) {
        return notifications.values().stream()
                .filter(n -> n.getRecipientId().equals(recipientId))
                .filter(n -> !onlyUnread || !n.isRead())
                .sorted(Comparator.comparingLong(WorkOrderNotification::getId).reversed())
                .toList();
    }

    @Override
    public void markRead(Long notificationId) {
        WorkOrderNotification notification = notifications.get(notificationId);
        if (notification != null) {
            notification.setRead(true);
        }
    }

    @Override
    public boolean markRead(Long notificationId, Long recipientId) {
        WorkOrderNotification notification = notifications.get(notificationId);
        if (notification == null || !recipientId.equals(notification.getRecipientId())) {
            return false;
        }
        notification.setRead(true);
        return true;
    }

    @Override
    public void markAllRead(Long recipientId) {
        notifications.values().stream()
                .filter(n -> n.getRecipientId().equals(recipientId))
                .forEach(n -> n.setRead(true));
    }

    @Override
    public long countUnread(Long recipientId) {
        return notifications.values().stream()
                .filter(n -> n.getRecipientId().equals(recipientId) && !n.isRead())
                .count();
    }
}
