package cn.slainc.core.event;

import java.util.LinkedHashMap;
import java.util.Map;

public class UserCreatedEvent extends DomainEvent {

    private final Long userId;

    private final String username;

    private final String createdBy;

    public UserCreatedEvent(Long userId, String username, String createdBy) {
        super("upms");
        this.userId = userId;
        this.username = username;
        this.createdBy = createdBy;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("createdBy", createdBy);
        return payload;
    }
}
