package cn.slainc.workorder.comment;

import cn.slainc.core.exception.BizException;
import cn.slainc.workorder.spi.WorkOrderCommentStore;
import cn.slainc.workorder.spi.WorkOrderStore;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;


public class WorkOrderCommentService {

    public static final long DEFAULT_EDIT_WINDOW_MILLIS = 5 * 60 * 1000L;

    private final WorkOrderCommentStore commentStore;

    private final WorkOrderStore orderStore;

    private final ApplicationEventPublisher publisher;

    private final long editWindowMillis;

    public WorkOrderCommentService(WorkOrderCommentStore commentStore, WorkOrderStore orderStore,
                                   ApplicationEventPublisher publisher) {
        this(commentStore, orderStore, publisher, DEFAULT_EDIT_WINDOW_MILLIS);
    }

    public WorkOrderCommentService(WorkOrderCommentStore commentStore, WorkOrderStore orderStore,
                                   ApplicationEventPublisher publisher, long editWindowMillis) {
        this.commentStore = commentStore;
        this.orderStore = orderStore;
        this.publisher = publisher;
        this.editWindowMillis = editWindowMillis;
    }

    public WorkOrderComment add(Long orderId, Long authorId, String content, String visibility, Long parentId,
                                Long quotedCommentId) {
        if (content == null || content.isBlank()) {
            throw new BizException("评论内容不能为空");
        }
        orderStore.findById(orderId).orElseThrow(() -> new BizException("工单不存在"));
        String effectiveVisibility = visibility == null || visibility.isBlank()
                ? WorkOrderComment.VISIBILITY_INTERNAL
                : visibility;
        if (!WorkOrderComment.VISIBILITY_INTERNAL.equals(effectiveVisibility)
                && !WorkOrderComment.VISIBILITY_EXTERNAL.equals(effectiveVisibility)) {
            throw new BizException("未知的评论可见性：" + effectiveVisibility);
        }
        requireSameOrder(parentId, orderId);
        requireSameOrder(quotedCommentId, orderId);
        long now = System.currentTimeMillis();
        WorkOrderComment comment = new WorkOrderComment();
        comment.setOrderId(orderId);
        comment.setAuthorId(authorId);
        comment.setContent(content.trim());
        comment.setVisibility(effectiveVisibility);
        comment.setParentId(parentId);
        comment.setQuotedCommentId(quotedCommentId);
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        commentStore.save(comment);
        publisher.publishEvent(new cn.slainc.workorder.event.WorkOrderCommentedEvent(
                orderId, orderNumber(orderId), comment.getId(), authorId));
        return comment;
    }

    public WorkOrderComment edit(Long commentId, Long editorId, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException("评论内容不能为空");
        }
        WorkOrderComment comment = requireComment(commentId);
        if (!comment.getAuthorId().equals(editorId)) {
            throw new BizException("只能编辑本人评论");
        }
        if (System.currentTimeMillis() - comment.getCreateTime() > editWindowMillis) {
            throw new BizException("已超过可编辑时限");
        }
        comment.setContent(content.trim());
        comment.setUpdateTime(System.currentTimeMillis());
        commentStore.save(comment);
        return comment;
    }

    public void delete(Long commentId, Long operatorId) {
        WorkOrderComment comment = requireComment(commentId);
        if (!comment.getAuthorId().equals(operatorId)) {
            throw new BizException("只能删除本人评论");
        }
        comment.setDeleted(true);
        comment.setUpdateTime(System.currentTimeMillis());
        commentStore.save(comment);
    }

    public void moderateDelete(Long commentId) {
        WorkOrderComment comment = requireComment(commentId);
        comment.setDeleted(true);
        comment.setUpdateTime(System.currentTimeMillis());
        commentStore.save(comment);
    }

    public WorkOrderComment like(Long commentId, boolean cancel) {
        WorkOrderComment comment = requireComment(commentId);
        comment.setLikeCount(cancel ? Math.max(0, comment.getLikeCount() - 1) : comment.getLikeCount() + 1);
        commentStore.save(comment);
        return comment;
    }

    public List<WorkOrderComment> listVisible(Long orderId, boolean externalVisible) {
        return commentStore.findByOrder(orderId).stream()
                .filter(c -> externalVisible || WorkOrderComment.VISIBILITY_INTERNAL.equals(c.getVisibility()))
                .toList();
    }

    private void requireSameOrder(Long commentId, Long orderId) {
        if (commentId == null) {
            return;
        }
        WorkOrderComment comment = requireComment(commentId);
        if (!comment.getOrderId().equals(orderId)) {
            throw new BizException("引用或回复的评论不属于该工单");
        }
    }

    private WorkOrderComment requireComment(Long commentId) {
        return commentStore.findById(commentId).orElseThrow(() -> new BizException("评论不存在或已删除"));
    }

    private String orderNumber(Long orderId) {
        return orderStore.findById(orderId).map(cn.slainc.workorder.domain.WorkOrder::getNumber).orElse(null);
    }
}
