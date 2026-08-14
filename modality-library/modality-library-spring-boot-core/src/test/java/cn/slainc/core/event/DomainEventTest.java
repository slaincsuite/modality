package cn.slainc.core.event;

import cn.slainc.core.context.ContextSnapshot;
import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainEventTest {

    @AfterEach
    void tearDown() {
        ContextSnapshot.clearAll();
    }

    @Test
    void capturesMetadataFromContext() {
        RequestContext.set(RequestContext.RequestInfo.of("trace-abc"));
        TenantContext.set(3L);
        UserCreatedEvent event = new UserCreatedEvent(10L, "bob", "admin");

        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
        assertEquals("upms", event.getSource());
        assertEquals("trace-abc", event.getTraceId());
        assertEquals(3L, (long) event.getTenantId());
        assertEquals(1, event.getEventVersion());
        assertEquals("UserCreatedEvent", event.getEventType());
    }

    @Test
    void serializesToJson() {
        UserCreatedEvent event = new UserCreatedEvent(10L, "bob", "admin");
        String json = event.toJson();
        assertTrue(json.contains("\"eventType\":\"UserCreatedEvent\""));
        assertTrue(json.contains("\"username\":\"bob\""));
        assertTrue(json.contains("\"createdBy\":\"admin\""));
        assertTrue(json.contains("\"payload\""));
        assertEquals(8, event.toMap().size());
        assertNull(event.toMap().get("tenantId"));
    }

    @Test
    void loginEventFactories() {
        LoginEvent success = LoginEvent.success(1L, "alice", LoginType.ADMIN, "10.0.0.1");
        assertTrue(success.isSucceeded());
        assertEquals("alice", success.getUsername());
        assertNull(success.getFailureReason());
        assertNull(success.getUserId() == null ? null : null);

        LoginEvent failure = LoginEvent.failure("alice", LoginType.ADMIN, "10.0.0.1", "密码错误");
        assertTrue(!failure.isSucceeded());
        assertNull(failure.getUserId());
        assertEquals("密码错误", failure.getFailureReason());
        assertTrue(failure.toJson().contains("密码错误"));
    }

    @Test
    void otherEventsCarryTheirFields() {
        LogoutEvent logout = new LogoutEvent(1L, "alice", LoginType.USER, true);
        assertTrue(logout.isForced());
        assertEquals("auth", logout.getSource());

        ConsentGrantedEvent granted = new ConsentGrantedEvent(1L, "client-a", Set.of("read", "write"));
        assertEquals(2, granted.getScopes().size());
        assertEquals("oauth2", granted.getSource());

        ConsentRevokedEvent revoked = new ConsentRevokedEvent(1L, "client-a", "用户解约");
        assertEquals("用户解约", revoked.getReason());

        ConfigChangedEvent changed = new ConfigChangedEvent("sm4.key", "crypto", "old", "new", false);
        assertEquals("sm4.key", changed.getConfigKey());
        assertEquals("config", changed.getSource());
        assertTrue(!changed.isRemoved());

        UserUpdatedEvent updated = new UserUpdatedEvent(1L, "bob", Set.of("email", "phone"));
        assertEquals(Set.of("email", "phone"), updated.getChangedFields());
        assertEquals("upms", updated.getSource());
        assertEquals(List.of("email", "phone").size(), updated.getChangedFields().size());
    }
}
