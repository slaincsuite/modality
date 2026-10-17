package cn.slainc.modules.workorder.web.portal;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
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
    @AuditLog(value = "标记工单通知已读", operation = AuditOperation.UPDATE, module = "workorder")
    public Object markRead(@PathVariable Long notificationId) {
        if (!notificationStore.markRead(notificationId, UserContext.userId())) {
            throw new BizException("通知不存在");
        }
        return Map.of("marked", true);
    }

    @PostMapping("/notifications/read-all")
    @AuditLog(value = "全部标记工单通知已读", operation = AuditOperation.UPDATE, module = "workorder")
    public Object markAllRead() {
        notificationStore.markAllRead(UserContext.userId());
        return Map.of("marked", true);
    }
}
