package cn.slainc.core.event;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConsentRevokedEvent extends DomainEvent {

    private final Long userId;

    private final String clientId;

    private final String reason;

    public ConsentRevokedEvent(Long userId, String clientId, String reason) {
        super("oauth2");
        this.userId = userId;
        this.clientId = clientId;
        this.reason = reason;
    }

    public Long getUserId() {
        return userId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("clientId", clientId);
        payload.put("reason", reason);
        return payload;
    }
}
