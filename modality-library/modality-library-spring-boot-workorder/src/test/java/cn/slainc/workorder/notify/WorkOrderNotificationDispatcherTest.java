package cn.slainc.workorder.notify;

import cn.slainc.workorder.event.WorkOrderAssignedEvent;
import cn.slainc.workorder.event.WorkOrderCreatedEvent;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import cn.slainc.workorder.spi.WorkOrderStore;
import cn.slainc.workorder.store.MemoryWorkOrderNotificationStore;
import cn.slainc.workorder.store.MemoryWorkOrderStore;
import cn.slainc.workorder.store.MemoryWorkOrderSubscriptionStore;
import cn.slainc.workorder.subscribe.WorkOrderSubscriptionService;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class WorkOrderNotificationDispatcherTest {

    private MemoryWorkOrderNotificationStore notificationStore;

    private WorkOrderNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        notificationStore = new MemoryWorkOrderNotificationStore();
        WorkOrderStore orderStore = new MemoryWorkOrderStore();
        cn.slainc.workorder.model.WorkOrder order = new cn.slainc.workorder.model.WorkOrder();
        order.setId(1L);
        order.setNumber("WO2026101300001");
        order.setTitle("登录页报错");
        order.setStatus("OPEN");
        order.setSubmitterId(101L);
        orderStore.save(order);
        MemoryWorkOrderSubscriptionStore subscriptionStore = new MemoryWorkOrderSubscriptionStore();
        WorkOrderSubscriptionService subscriptionService = new WorkOrderSubscriptionService(
                subscriptionStore, orderStore);
        subscriptionService.subscribeOrder(1L, 999L);
        dispatcher = new WorkOrderNotificationDispatcher(orderStore, subscriptionStore,
                notificationStore, List.of(new InAppWorkOrderNotificationProvider(notificationStore)));
    }

    @Test
    void assignedEventNotifiesAssigneeSubmitterAndSubscriberButNotOperator() {
        dispatcher.onDomainEvent(new WorkOrderAssignedEvent(1L, "WO2026101300001", 202L, 9L));
        List<WorkOrderNotification> all = notificationStore.findByRecipient(202L, false);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getTitle()).contains("WO2026101300001");
        assertThat(notificationStore.findByRecipient(101L, false)).hasSize(1);
        assertThat(notificationStore.findByRecipient(999L, false)).hasSize(1);
        assertThat(notificationStore.findByRecipient(9L, false)).isEmpty();
    }

    @Test
    void duplicateEventWithinWindowIsDeduplicated() {
        dispatcher.onDomainEvent(new WorkOrderAssignedEvent(1L, "WO2026101300001", 202L, 9L));
        dispatcher.onDomainEvent(new WorkOrderAssignedEvent(1L, "WO2026101300001", 202L, 9L));
        assertThat(notificationStore.findByRecipient(202L, false)).hasSize(1);
    }

    @Test
    void markReadAndUnreadCountWork() {
        dispatcher.onDomainEvent(new WorkOrderAssignedEvent(1L, "WO2026101300001", 202L, 9L));
        assertThat(notificationStore.countUnread(202L)).isEqualTo(1);
        WorkOrderNotification notification = notificationStore.findByRecipient(202L, true).get(0);
        notificationStore.markRead(notification.getId());
        assertThat(notificationStore.countUnread(202L)).isZero();
        notificationStore.markAllRead(202L);
    }
}
