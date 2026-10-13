package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderTransitionedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private String action;

    private String sourceStatus;

    private String targetStatus;

    private Long operatorId;

    public WorkOrderTransitionedEvent(Long orderId, String number, String action, String sourceStatus, String targetStatus, Long operatorId) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.action = action;
        this.sourceStatus = sourceStatus;
        this.targetStatus = targetStatus;
        this.operatorId = operatorId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public String getAction() {
        return action;
    }

    public String getSourceStatus() {
        return sourceStatus;
    }

    public String getTargetStatus() {
        return targetStatus;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("action", action);
        payload.put("sourceStatus", sourceStatus);
        payload.put("targetStatus", targetStatus);
        payload.put("operatorId", operatorId);
        return payload;
    }
}
