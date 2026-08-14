package cn.slainc.core.event;

import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.utils.json.JsonUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public abstract class DomainEvent implements Serializable {

    private final String eventId;

    private final LocalDateTime occurredAt;

    private final String source;

    private final String traceId;

    private final Long tenantId;

    private final int eventVersion;

    protected DomainEvent(String source) {
        this(source, 1);
    }

    protected DomainEvent(String source, int eventVersion) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
        this.source = source;
        this.eventVersion = eventVersion;
        this.traceId = RequestContext.traceId();
        this.tenantId = TenantContext.get();
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getSource() {
        return source;
    }

    public String getTraceId() {
        return traceId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getEventType() {
        return getClass().getSimpleName();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", eventId);
        map.put("eventType", getEventType());
        map.put("eventVersion", eventVersion);
        map.put("occurredAt", occurredAt);
        map.put("source", source);
        map.put("traceId", traceId);
        map.put("tenantId", tenantId);
        map.put("payload", payload());
        return map;
    }

    protected Object payload() {
        return null;
    }

    public String toJson() {
        return JsonUtils.toJson(toMap());
    }
}
