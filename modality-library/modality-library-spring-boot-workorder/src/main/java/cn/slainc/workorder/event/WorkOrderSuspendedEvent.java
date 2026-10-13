package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderSuspendedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private String reason;

    private Long operatorId;

    public WorkOrderSuspendedEvent(Long orderId, String number, String reason, Long operatorId) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.reason = reason;
        this.operatorId = operatorId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
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
        payload.put("reason", reason);
        payload.put("operatorId", operatorId);
        return payload;
    }
}
