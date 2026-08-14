package cn.slainc.core.event;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ConsentGrantedEvent extends DomainEvent {

    private final Long userId;

    private final String clientId;

    private final Set<String> scopes;

    public ConsentGrantedEvent(Long userId, String clientId, Set<String> scopes) {
        super("oauth2");
        this.userId = userId;
        this.clientId = clientId;
        this.scopes = scopes == null ? Set.of() : new LinkedHashSet<>(scopes);
    }

    public Long getUserId() {
        return userId;
    }

    public String getClientId() {
        return clientId;
    }

    public Set<String> getScopes() {
        return Set.copyOf(scopes);
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("clientId", clientId);
        payload.put("scopes", scopes);
        return payload;
    }
}
