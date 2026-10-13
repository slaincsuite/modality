package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderTransferredEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private Long fromAssigneeId;

    private Long toAssigneeId;

    private String reason;

    private Long operatorId;

    public WorkOrderTransferredEvent(Long orderId, String number, Long fromAssigneeId, Long toAssigneeId, String reason, Long operatorId) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.fromAssigneeId = fromAssigneeId;
        this.toAssigneeId = toAssigneeId;
        this.reason = reason;
        this.operatorId = operatorId;
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

    public Long getOperatorId() {
        return operatorId;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("fromAssigneeId", fromAssigneeId);
        payload.put("toAssigneeId", toAssigneeId);
        payload.put("reason", reason);
        payload.put("operatorId", operatorId);
        return payload;
    }
}
