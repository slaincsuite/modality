package cn.slainc.modules.workorder.web;

import cn.slainc.core.context.UserContext;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;


@Tag(name = "工单通知", description = "站内通知的查询与已读")
@RestController
@RequestMapping("/workorder/my")
@RequireLogin
public class WorkOrderNotificationPortalController {

    private final WorkOrderNotificationStore notificationStore;

    public WorkOrderNotificationPortalController(WorkOrderNotificationStore notificationStore) {
        this.notificationStore = notificationStore;
    }

    @GetMapping("/notifications")
    public Object notifications(@RequestParam(defaultValue = "false") boolean onlyUnread) {
        return notificationStore.findByRecipient(UserContext.userId(), onlyUnread);
    }

    @GetMapping("/notifications/unread-count")
    public Object unreadCount() {
        return notificationStore.countUnread(UserContext.userId());
    }

    @PostMapping("/notifications/{notificationId}/read")
    public Object markRead(@PathVariable Long notificationId) {
        notificationStore.markRead(notificationId);
        return Map.of("marked", true);
    }

    @PostMapping("/notifications/read-all")
    public Object markAllRead() {
        notificationStore.markAllRead(UserContext.userId());
        return Map.of("marked", true);
    }
}
