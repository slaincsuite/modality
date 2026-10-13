package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderCreatedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private String typeCode;

    private Long submitterId;

    public WorkOrderCreatedEvent(Long orderId, String number, String typeCode, Long submitterId) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.typeCode = typeCode;
        this.submitterId = submitterId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public Long getSubmitterId() {
        return submitterId;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("typeCode", typeCode);
        payload.put("submitterId", submitterId);
        return payload;
    }
}
