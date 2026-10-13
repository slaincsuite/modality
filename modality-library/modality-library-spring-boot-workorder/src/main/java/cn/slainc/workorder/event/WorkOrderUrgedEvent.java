package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderUrgedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private Long urgedUserId;

    private Long operatorId;

    private String message;

    public WorkOrderUrgedEvent(Long orderId, String number, Long urgedUserId, Long operatorId, String message) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.urgedUserId = urgedUserId;
        this.operatorId = operatorId;
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public Long getUrgedUserId() {
        return urgedUserId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getMessage() {
        return message;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("urgedUserId", urgedUserId);
        payload.put("operatorId", operatorId);
        payload.put("message", message);
        return payload;
    }
}
