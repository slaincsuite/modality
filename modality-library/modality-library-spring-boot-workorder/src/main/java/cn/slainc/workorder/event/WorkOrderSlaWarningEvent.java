package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderSlaWarningEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private String slaCode;

    private long remainingMillis;

    public WorkOrderSlaWarningEvent(Long orderId, String number, String slaCode, long remainingMillis) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.slaCode = slaCode;
        this.remainingMillis = remainingMillis;
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

    public long getRemainingMillis() {
        return remainingMillis;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("slaCode", slaCode);
        payload.put("remainingMillis", remainingMillis);
        return payload;
    }
}
