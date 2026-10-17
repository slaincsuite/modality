package cn.slainc.modules.workorder.web.portal;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.workorder.dto.WorkOrderCommentRequest;
import cn.slainc.modules.workorder.service.order.WorkOrderService;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.workorder.comment.WorkOrderCommentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;


@Tag(name = "工单评论", description = "评论、回复、引用与点赞")
@RestController
@RequestMapping("/workorder/my")
@RequireLogin
public class WorkOrderCommentPortalController {

    private final WorkOrderService workOrderService;

    private final WorkOrderCommentService commentService;

    public WorkOrderCommentPortalController(WorkOrderService workOrderService,
                                            WorkOrderCommentService commentService) {
        this.workOrderService = workOrderService;
        this.commentService = commentService;
    }

    @PostMapping("/{id}/comments")
    @AuditLog(value = "发表工单评论", operation = AuditOperation.CREATE, module = "workorder")
    public Object comment(@PathVariable Long id, @RequestBody WorkOrderCommentRequest request) {
        WorkOrderCommentRequest actual = request == null
                ? new WorkOrderCommentRequest(null, null, null, null)
                : request;
        return commentService.add(id, UserContext.userId(), actual.content(), actual.visibility(),
                actual.parentId(), actual.quotedCommentId());
    }

    @GetMapping("/{id}/comments")
    public Object comments(@PathVariable Long id) {
        workOrderService.requireOwnOrder(id);
        return commentService.listVisible(id, false);
    }

    @PostMapping("/comments/{commentId}")
    @AuditLog(value = "编辑工单评论", operation = AuditOperation.UPDATE, module = "workorder")
    public Object editComment(@PathVariable Long commentId, @RequestBody Map<String, String> body) {
        return commentService.edit(commentId, UserContext.userId(), body == null ? null : body.get("content"));
    }

    @PostMapping("/comments/{commentId}/like")
    @AuditLog(value = "点赞工单评论", operation = AuditOperation.UPDATE, module = "workorder")
    public Object likeComment(@PathVariable Long commentId,
                              @RequestParam(defaultValue = "false") boolean cancel) {
        return commentService.like(commentId, cancel);
    }

    @DeleteMapping("/comments/{commentId}")
    @AuditLog(value = "删除工单评论", operation = AuditOperation.DELETE, module = "workorder")
    public Object deleteComment(@PathVariable Long commentId) {
        commentService.delete(commentId, UserContext.userId());
        return Map.of("deleted", true);
    }
}
