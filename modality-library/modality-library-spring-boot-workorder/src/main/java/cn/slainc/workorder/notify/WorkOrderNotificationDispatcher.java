package cn.slainc.workorder.notify;

import cn.slainc.core.event.DomainEvent;
import cn.slainc.workorder.event.WorkOrderAssignedEvent;
import cn.slainc.workorder.event.WorkOrderClaimedEvent;
import cn.slainc.workorder.event.WorkOrderClosedEvent;
import cn.slainc.workorder.event.WorkOrderCommentedEvent;
import cn.slainc.workorder.event.WorkOrderCreatedEvent;
import cn.slainc.workorder.event.WorkOrderDeletedEvent;
import cn.slainc.workorder.event.WorkOrderEscalatedEvent;
import cn.slainc.workorder.event.WorkOrderReopenedEvent;
import cn.slainc.workorder.event.WorkOrderResumedEvent;
import cn.slainc.workorder.event.WorkOrderSuspendedEvent;
import cn.slainc.workorder.event.WorkOrderTransferredEvent;
import cn.slainc.workorder.event.WorkOrderTransitionedEvent;
import cn.slainc.workorder.event.WorkOrderUrgedEvent;
import cn.slainc.workorder.domain.WorkOrder;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import cn.slainc.workorder.spi.WorkOrderStore;
import cn.slainc.workorder.spi.WorkOrderSubscriptionStore;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;


public class WorkOrderNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderNotificationDispatcher.class);

    private static final long DEDUP_WINDOW_MILLIS = 1000;

    private static final Map<String, String> TEMPLATES = Map.ofEntries(
            Map.entry("WorkOrderCreatedEvent", "工单 {number} 已创建"),
            Map.entry("WorkOrderAssignedEvent", "工单 {number} 已分配给你"),
            Map.entry("WorkOrderClaimedEvent", "工单 {number} 已被认领"),
            Map.entry("WorkOrderCommentedEvent", "工单 {number} 有新评论"),
            Map.entry("WorkOrderTransitionedEvent", "工单 {number} 状态发生流转"),
            Map.entry("WorkOrderClosedEvent", "工单 {number} 已关闭"),
            Map.entry("WorkOrderReopenedEvent", "工单 {number} 已重新开启"),
            Map.entry("WorkOrderDeletedEvent", "工单 {number} 已删除"),
            Map.entry("WorkOrderTransferredEvent", "工单 {number} 已转派给你"),
            Map.entry("WorkOrderEscalatedEvent", "工单 {number} 已升级给你处理"),
            Map.entry("WorkOrderSuspendedEvent", "工单 {number} 已挂起"),
            Map.entry("WorkOrderResumedEvent", "工单 {number} 已恢复处理"),
            Map.entry("WorkOrderUrgedEvent", "工单 {number} 被催办，请尽快处理"),
            Map.entry("WorkOrderSlaWarningEvent", "工单 {number} 即将超出 SLA 时限"),
            Map.entry("WorkOrderSlaBreachedEvent", "工单 {number} 已超出 SLA 时限"));

    private final WorkOrderStore orderStore;

    private final WorkOrderSubscriptionStore subscriptionStore;

    private final WorkOrderNotificationStore notificationStore;

    private final List<? extends WorkOrderNotificationProvider> providers;

    private final Map<String, Long> recentSends = new ConcurrentHashMap<>();

    public WorkOrderNotificationDispatcher(WorkOrderStore orderStore,
                                           WorkOrderSubscriptionStore subscriptionStore,
                                           WorkOrderNotificationStore notificationStore,
                                           List<? extends WorkOrderNotificationProvider> providers) {
        this.orderStore = orderStore;
        this.subscriptionStore = subscriptionStore;
        this.notificationStore = notificationStore;
        this.providers = List.copyOf(providers);
    }

    @EventListener(DomainEvent.class)
    public void onDomainEvent(DomainEvent event) {
        String eventCode = event.getClass().getSimpleName();
        Long orderId = orderIdOf(event);
        if (orderId == null) {
            return;
        }
        WorkOrder order = orderStore.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        Set<Long> recipients = recipientsOf(event, order);
        recipients.remove(actorOf(event));
        for (Long recipient : recipients) {
            if (recipient == null || recentlySent(eventCode, orderId, recipient)) {
                continue;
            }
            dispatch(eventCode, order, recipient);
        }
    }

    private void dispatch(String eventCode, WorkOrder order, Long recipient) {
        String title = TEMPLATES.getOrDefault(eventCode, "工单 {number} 有新的动态")
                .replace("{number}", order.getNumber());
        for (WorkOrderNotificationProvider provider : providers) {
            WorkOrderNotification notification = newNotification(order, recipient, eventCode, title,
                    provider.channel());
            boolean delivered = false;
            for (int attempt = 0; attempt < 2 && !delivered; attempt++) {
                try {
                    provider.send(notification);
                    delivered = true;
                } catch (RuntimeException exception) {
                    log.warn("工单通知投递失败（第 {} 次），渠道 {}，收件人 {}", attempt + 1, provider.channel(),
                            recipient, exception);
                }
            }
        }
    }

    private WorkOrderNotification newNotification(WorkOrder order, Long recipient, String eventCode, String title,
                                                  String channel) {
        WorkOrderNotification notification = new WorkOrderNotification();
        notification.setOrderId(order.getId());
        notification.setNumber(order.getNumber());
        notification.setRecipientId(recipient);
        notification.setEventCode(eventCode);
        notification.setTitle(title);
        notification.setContent(title + "：" + order.getTitle());
        notification.setChannel(channel);
        notification.setCreateTime(System.currentTimeMillis());
        return notification;
    }

    private Set<Long> recipientsOf(DomainEvent event, WorkOrder order) {
        Set<Long> recipients = new HashSet<>();
        if (event instanceof WorkOrderCreatedEvent created) {
            recipients.addAll(subscribers(order.getId()));
        } else if (event instanceof WorkOrderAssignedEvent assigned) {
            recipients.add(assigned.getAssigneeId());
            recipients.add(order.getSubmitterId());
            recipients.addAll(subscribers(order.getId()));
        } else if (event instanceof WorkOrderClaimedEvent claimed) {
            recipients.add(order.getSubmitterId());
            recipients.addAll(subscribers(order.getId()));
        } else if (event instanceof WorkOrderCommentedEvent commented) {
            recipients.add(commented.getReplierId());
            recipients.add(order.getSubmitterId());
            recipients.add(order.getAssigneeId());
            recipients.addAll(subscribers(order.getId()));
        } else if (event instanceof WorkOrderTransitionedEvent transitioned) {
            if (!Set.of("assign", "claim", "transfer", "escalate", "close", "reopen")
                    .contains(transitioned.getAction())) {
                recipients.add(order.getSubmitterId());
                recipients.add(order.getAssigneeId());
                recipients.addAll(subscribers(order.getId()));
            }
        } else if (event instanceof WorkOrderClosedEvent || event instanceof WorkOrderReopenedEvent) {
            recipients.add(order.getSubmitterId());
            recipients.add(order.getAssigneeId());
            recipients.addAll(subscribers(order.getId()));
        } else if (event instanceof WorkOrderTransferredEvent transferred) {
            recipients.add(transferred.getToAssigneeId());
            recipients.add(order.getSubmitterId());
        } else if (event instanceof WorkOrderEscalatedEvent escalated) {
            recipients.add(escalated.getToAssigneeId());
        } else if (event instanceof WorkOrderSuspendedEvent suspended) {
            recipients.add(order.getSubmitterId());
            recipients.add(order.getAssigneeId());
        } else if (event instanceof WorkOrderResumedEvent) {
            recipients.add(order.getSubmitterId());
        } else if (event instanceof WorkOrderDeletedEvent) {
            recipients.addAll(subscribers(order.getId()));
        } else if (event instanceof WorkOrderUrgedEvent urged) {
            recipients.add(urged.getUrgedUserId());
        } else if (event instanceof cn.slainc.workorder.event.WorkOrderSlaWarningEvent slaWarning) {
            recipients.add(order.getAssigneeId());
        } else if (event instanceof cn.slainc.workorder.event.WorkOrderSlaBreachedEvent slaBreached) {
            recipients.add(order.getAssigneeId());
        }
        return recipients;
    }

    private Long orderIdOf(DomainEvent event) {
        if (event instanceof WorkOrderCreatedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderAssignedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderClaimedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderCommentedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderTransitionedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderClosedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderReopenedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderDeletedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderTransferredEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderEscalatedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderSuspendedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderResumedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof WorkOrderUrgedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof cn.slainc.workorder.event.WorkOrderSlaWarningEvent e) {
            return e.getOrderId();
        }
        if (event instanceof cn.slainc.workorder.event.WorkOrderSlaBreachedEvent e) {
            return e.getOrderId();
        }
        if (event instanceof cn.slainc.workorder.event.WorkOrderUpdatedEvent e) {
            return e.getOrderId();
        }
        return null;
    }

    private Long actorOf(DomainEvent event) {
        if (event instanceof WorkOrderAssignedEvent e) {
            return e.getOperatorId();
        }
        if (event instanceof WorkOrderClaimedEvent e) {
            return e.getClaimantId();
        }
        if (event instanceof WorkOrderCommentedEvent e) {
            return e.getReplierId();
        }
        if (event instanceof WorkOrderTransitionedEvent e) {
            return e.getOperatorId();
        }
        if (event instanceof WorkOrderClosedEvent e) {
            return e.getOperatorId();
        }
        if (event instanceof WorkOrderReopenedEvent e) {
            return e.getOperatorId();
        }
        if (event instanceof WorkOrderTransferredEvent e) {
            return e.getOperatorId();
        }
        if (event instanceof WorkOrderEscalatedEvent e) {
            return e.getFromAssigneeId();
        }
        if (event instanceof WorkOrderSuspendedEvent e) {
            return e.getOperatorId();
        }
        if (event instanceof WorkOrderResumedEvent e) {
            return e.getOperatorId();
        }
        if (event instanceof WorkOrderUrgedEvent e) {
            return e.getOperatorId();
        }
        return null;
    }

    private Set<Long> subscribers(Long orderId) {
        return new HashSet<>(subscriptionStore.findByOrder(orderId).stream()
                .map(cn.slainc.workorder.subscribe.WorkOrderSubscription::getSubscriberId)
                .toList());
    }

    private boolean recentlySent(String eventCode, Long orderId, Long recipient) {
        String key = eventCode + ":" + orderId + ":" + recipient;
        long now = System.currentTimeMillis();
        recentSends.values().removeIf(time -> now - time > DEDUP_WINDOW_MILLIS);
        Long last = recentSends.put(key, now);
        return last != null && now - last < DEDUP_WINDOW_MILLIS;
    }
}
