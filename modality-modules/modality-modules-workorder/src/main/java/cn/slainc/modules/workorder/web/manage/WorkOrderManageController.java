package cn.slainc.modules.workorder.web.manage;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.workorder.dto.WorkOrderCommentRequest;
import cn.slainc.modules.workorder.dto.WorkOrderFilter;
import cn.slainc.modules.workorder.WorkorderKeys;
import cn.slainc.modules.workorder.excel.WorkOrderExportService;
import cn.slainc.modules.workorder.service.order.WorkOrderService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.workorder.comment.WorkOrderCommentService;
import cn.slainc.workorder.comment.WorkOrderComment;
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
                       @RequestParam(required = false) String typeCode,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return workOrderService.pageManage(new WorkOrderFilter(status, priority, typeCode, keyword, from, to),
                current, size);
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

    @GetMapping("/{id}/comments")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_QUERY)
    public Object comments(@PathVariable Long id) {
        workOrderService.requireOrder(id);
        return commentService.listVisible(id, true);
    }

    @PostMapping("/{id}/comments")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_COMMENT_MANAGE)
    @AuditLog(value = "管理端回复工单评论", operation = AuditOperation.CREATE, module = "workorder")
    public Object addComment(@PathVariable Long id, @RequestBody WorkOrderCommentRequest request) {
        WorkOrderCommentRequest actual = request == null
                ? new WorkOrderCommentRequest(null, null, null, null)
                : request;
        String visibility = actual.visibility() == null || actual.visibility().isBlank()
                ? WorkOrderComment.VISIBILITY_INTERNAL
                : actual.visibility();
        return commentService.add(id, UserContext.userId(), actual.content(), visibility,
                actual.parentId(), actual.quotedCommentId());
    }

    @DeleteMapping("/comments/{commentId}")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_COMMENT_MANAGE)
    @AuditLog(value = "删除工单评论", operation = AuditOperation.DELETE, module = "workorder")
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
    @AuditLog(value = "标记工单通知已读", operation = AuditOperation.UPDATE, module = "workorder")
    public Object markRead(@PathVariable Long notificationId) {
        notificationStore.markRead(notificationId);
        return Map.of("marked", true);
    }

    @PostMapping("/notifications/read-all")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_NOTIFICATION_MANAGE)
    @AuditLog(value = "全部标记工单通知已读", operation = AuditOperation.UPDATE, module = "workorder")
    public Object markAllRead(@RequestParam Long recipientId) {
        notificationStore.markAllRead(recipientId);
        return Map.of("marked", true);
    }

    @PostMapping("/{id}/assign")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "分派工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object assign(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return workOrderService.assign(id, body == null ? null : body.get("assigneeId"));
    }

    @PostMapping("/{id}/close")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_CLOSE)
    @AuditLog(value = "关闭工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object close(@PathVariable Long id) {
        return workOrderService.close(id);
    }

    @GetMapping("/export")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_EXPORT)
    @AuditLog(value = "导出工单", operation = AuditOperation.EXPORT, category = AuditCategory.DATA,
            module = "workorder")
    public void export(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String priority,
                       @RequestParam(required = false) String typeCode,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       HttpServletResponse response) {
        exportService.export(status, priority, typeCode, keyword, from, to, response);
    }
}
