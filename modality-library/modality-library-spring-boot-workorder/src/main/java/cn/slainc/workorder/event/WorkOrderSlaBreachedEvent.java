package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderSlaBreachedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private String slaCode;

    private long overdueMillis;

    public WorkOrderSlaBreachedEvent(Long orderId, String number, String slaCode, long overdueMillis) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.slaCode = slaCode;
        this.overdueMillis = overdueMillis;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public String getSlaCode() {
        return slaCode;
    }

    public long getOverdueMillis() {
        return overdueMillis;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("slaCode", slaCode);
        payload.put("overdueMillis", overdueMillis);
        return payload;
    }
}
