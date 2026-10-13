package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderResumedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private Long operatorId;

    public WorkOrderResumedEvent(Long orderId, String number, Long operatorId) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.operatorId = operatorId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("operatorId", operatorId);
        return payload;
    }
}
