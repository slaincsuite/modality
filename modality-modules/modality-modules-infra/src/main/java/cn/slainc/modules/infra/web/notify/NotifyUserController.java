package cn.slainc.modules.infra.web.notify;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.service.notify.NotifyService;
import cn.slainc.security.annotation.RequirePermission;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "我的通知", description = "站内信查询与已读处理")
@RestController
@RequestMapping("/infra/notify/my")
public class NotifyUserController {

    private final NotifyService notifyService;

    private final CurrentUser currentUser;

    public NotifyUserController(NotifyService notifyService, CurrentUser currentUser) {
        this.notifyService = notifyService;
        this.currentUser = currentUser;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_QUERY)
    @GetMapping("/page")
    public Result<PageResult<NotifyService.MessageView>> page(@RequestParam(defaultValue = "1") long current,
                                                               @RequestParam(defaultValue = "10") long size,
                                                               @RequestParam(required = false) Boolean read,
                                                               @RequestParam(required = false) String keyword) {
        return Result.ok(notifyService.myMessages(currentUser.userId(), currentUser.loginType(), current, size,
                read, keyword));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_QUERY)
    @GetMapping("/{id}")
    public Result<NotifyService.MessageView> detail(@PathVariable long id) {
        return Result.ok(notifyService.findMyMessage(currentUser.userId(), currentUser.loginType(), id));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_QUERY)
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(notifyService.unreadCount(currentUser.userId(), currentUser.loginType()));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "标记站内信已读", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable long id) {
        notifyService.markRead(currentUser.userId(), currentUser.loginType(), id);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "标记全部站内信已读", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/read-all")
    public Result<Long> markAllRead() {
        return Result.ok(notifyService.markAllRead(currentUser.userId(), currentUser.loginType()));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_DELETE)
    @AuditLog(value = "删除站内信", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "infra")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        notifyService.deleteMessage(currentUser.userId(), currentUser.loginType(), id);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_DELETE)
    @AuditLog(value = "批量删除站内信", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "infra")
    @DeleteMapping
    public Result<Integer> deleteBatch(@RequestParam List<Long> ids) {
        return Result.ok(notifyService.deleteMessages(currentUser.userId(), currentUser.loginType(), ids));
    }
}
