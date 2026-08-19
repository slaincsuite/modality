package cn.slainc.web.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketHeartbeatTest {

    private final MutableClock clock = new MutableClock();

    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry();

    private final WebSocketHeartbeat heartbeat = createHeartbeat();

    private WebSocketHeartbeat createHeartbeat() {
        WebSocketProperties properties = new WebSocketProperties();
        properties.setHeartbeatInterval(Duration.ofSeconds(1));
        properties.setHeartbeatTimeout(Duration.ofSeconds(5));
        return new WebSocketHeartbeat(registry, properties, clock);
    }

    @AfterEach
    void tearDown() {
        heartbeat.stop();
    }

    private WebSocketSession session(String id) throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void closesSessionAfterHeartbeatTimeout() throws Exception {
        WebSocketSession session = session("s1");
        registry.register(new WebSocketUser("u1", "u1"), session);

        clock.advance(Duration.ofSeconds(6));

        assertEquals(1, heartbeat.sweep());
        verify(session).close(any(CloseStatus.class));
        assertEquals(0, registry.size());
    }

    @Test
    void sendsPingToLiveSession() throws Exception {
        WebSocketSession session = session("s1");
        registry.register(new WebSocketUser("u1", "u1"), session);
        registry.touchActive("s1");

        clock.advance(Duration.ofSeconds(2));

        assertEquals(0, heartbeat.sweep());
        verify(session).sendMessage(any(PingMessage.class));
        assertEquals(1, registry.size());
    }

    @Test
    void removesSessionWhenPingFails() throws Exception {
        WebSocketSession session = session("s1");
        registry.register(new WebSocketUser("u1", "u1"), session);
        registry.touchActive("s1");
        doThrow(new IOException("连接已断开")).when(session).sendMessage(any(PingMessage.class));

        clock.advance(Duration.ofSeconds(2));

        assertEquals(1, heartbeat.sweep());
        assertEquals(0, registry.size());
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    void lifecycleFlagsFollowStartAndStop() {
        assertEquals(false, heartbeat.isRunning());
        heartbeat.start();
        assertEquals(true, heartbeat.isRunning());
        heartbeat.stop();
        assertEquals(false, heartbeat.isRunning());
    }

    static class MutableClock extends Clock {

        private Instant instant = Instant.now();

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
