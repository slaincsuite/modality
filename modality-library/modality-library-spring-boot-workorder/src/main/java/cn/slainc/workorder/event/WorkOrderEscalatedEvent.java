package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderEscalatedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private Long fromAssigneeId;

    private Long toAssigneeId;

    private String reason;

    public WorkOrderEscalatedEvent(Long orderId, String number, Long fromAssigneeId, Long toAssigneeId, String reason) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.fromAssigneeId = fromAssigneeId;
        this.toAssigneeId = toAssigneeId;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public Long getFromAssigneeId() {
        return fromAssigneeId;
    }

    public Long getToAssigneeId() {
        return toAssigneeId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("fromAssigneeId", fromAssigneeId);
        payload.put("toAssigneeId", toAssigneeId);
        payload.put("reason", reason);
        return payload;
    }
}
