package cn.slainc.core.event;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class UserUpdatedEvent extends DomainEvent {

    private final Long userId;

    private final String username;

    private final Set<String> changedFields;

    public UserUpdatedEvent(Long userId, String username, Set<String> changedFields) {
        super("upms");
        this.userId = userId;
        this.username = username;
        this.changedFields = changedFields == null ? Set.of() : new LinkedHashSet<>(changedFields);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Set<String> getChangedFields() {
        return Set.copyOf(changedFields);
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("changedFields", changedFields);
        return payload;
    }
}
