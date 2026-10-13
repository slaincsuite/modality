package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderDeletedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    public WorkOrderDeletedEvent(Long orderId, String number) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);

        return payload;
    }
}
