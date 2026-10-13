package cn.slainc.workorder.event;

import cn.slainc.core.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkOrderClaimedEvent extends DomainEvent {

    private Long orderId;

    private String number;

    private Long claimantId;

    public WorkOrderClaimedEvent(Long orderId, String number, Long claimantId) {
        super("workorder");
        this.orderId = orderId;
        this.number = number;
        this.claimantId = claimantId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public Long getClaimantId() {
        return claimantId;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("number", number);
        payload.put("claimantId", claimantId);
        return payload;
    }
}
