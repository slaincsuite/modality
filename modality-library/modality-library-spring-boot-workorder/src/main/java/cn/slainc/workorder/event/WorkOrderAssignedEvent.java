package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderAssignedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private Long assigneeId;

    private Long operatorId;

    public WorkOrderAssignedEvent(Long orderId, String number, Long assigneeId, Long operatorId) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.assigneeId = assigneeId;
        this.operatorId = operatorId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("assigneeId", assigneeId);
        payload.put("operatorId", operatorId);
        return payload;
    }
}
