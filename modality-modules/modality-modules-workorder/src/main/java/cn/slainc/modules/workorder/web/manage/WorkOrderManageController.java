package cn.slainc.modules.workorder.web.manage;

import cn.slainc.modules.workorder.WorkorderKeys;
import cn.slainc.modules.workorder.excel.WorkOrderExportService;
import cn.slainc.modules.workorder.service.order.WorkOrderService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.workorder.comment.WorkOrderCommentService;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;


@Tag(name = "工单管理", description = "工单查询、分派与状态流转")
@RestController
@RequestMapping("/workorder/manage")
public class WorkOrderManageController {

    private final WorkOrderService workOrderService;

    private final WorkOrderExportService exportService;

    private final WorkOrderCommentService commentService;

    private final WorkOrderNotificationStore notificationStore;

    public WorkOrderManageController(WorkOrderService workOrderService, WorkOrderExportService exportService,
                                     WorkOrderCommentService commentService,
                                     WorkOrderNotificationStore notificationStore) {
        this.workOrderService = workOrderService;
        this.exportService = exportService;
        this.commentService = commentService;
        this.notificationStore = notificationStore;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_QUERY)
    public Object page(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String priority,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return workOrderService.pageManage(status, priority, keyword, current, size);
    }

    @GetMapping("/{id}")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_QUERY)
    public Object detail(@PathVariable Long id) {
        return workOrderService.requireOrder(id);
    }

    @GetMapping("/{id}/replies")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_QUERY)
    public Object replies(@PathVariable Long id) {
        return workOrderService.replies(id);
    }

    @DeleteMapping("/comments/{commentId}")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_COMMENT_MANAGE)
    public Object moderateDeleteComment(@PathVariable Long commentId) {
        commentService.moderateDelete(commentId);
        return Map.of("deleted", true);
    }

    @GetMapping("/notifications")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_NOTIFICATION_MANAGE)
    public Object notifications(@RequestParam Long recipientId,
                                @RequestParam(defaultValue = "false") boolean onlyUnread) {
        return notificationStore.findByRecipient(recipientId, onlyUnread);
    }

    @PostMapping("/notifications/{notificationId}/read")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_NOTIFICATION_MANAGE)
    public Object markRead(@PathVariable Long notificationId) {
        notificationStore.markRead(notificationId);
        return Map.of("marked", true);
    }

    @PostMapping("/notifications/read-all")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_NOTIFICATION_MANAGE)
    public Object markAllRead(@RequestParam Long recipientId) {
        notificationStore.markAllRead(recipientId);
        return Map.of("marked", true);
    }

    @PostMapping("/{id}/assign")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    public Object assign(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return workOrderService.assign(id, body == null ? null : body.get("assigneeId"));
    }

    @PostMapping("/{id}/close")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_CLOSE)
    public Object close(@PathVariable Long id) {
        return workOrderService.close(id);
    }

    @GetMapping("/export")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_EXPORT)
    public void export(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String priority,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) {
        exportService.export(status, priority, keyword, response);
    }
}
