package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderCommentedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private Long commentId;

    private Long replierId;

    public WorkOrderCommentedEvent(Long orderId, String number, Long commentId, Long replierId) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.commentId = commentId;
        this.replierId = replierId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public Long getCommentId() {
        return commentId;
    }

    public Long getReplierId() {
        return replierId;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("commentId", commentId);
        payload.put("replierId", replierId);
        return payload;
    }
}
