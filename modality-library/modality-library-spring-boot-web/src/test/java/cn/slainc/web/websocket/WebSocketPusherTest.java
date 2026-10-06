package cn.slainc.web.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketPusherTest {

    private static ObjectProvider<WebSocketBroadcaster> provider(WebSocketBroadcaster broadcaster) {
        return new ObjectProvider<>() {
            @Override
            public WebSocketBroadcaster getObject() {
                if (broadcaster == null) {
                    throw new IllegalStateException("没有可用的 WebSocketBroadcaster");
                }
                return broadcaster;
            }

            @Override
            public WebSocketBroadcaster getIfAvailable() {
                return broadcaster;
            }
        };
    }

    @Test
    void sendToUserSerializesPayloadAndReturnsTrue() {
        RecordingBroadcaster recorder = new RecordingBroadcaster();
        WebSocketPusher pusher = new WebSocketPusher(provider(recorder));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "logout");
        assertTrue(pusher.sendToUser("1001", payload));
        assertEquals("1001", recorder.userId);
        assertEquals("{\"event\":\"logout\"}", recorder.payload);
    }

    @Test
    void broadcastPassesStringPayloadThrough() {
        RecordingBroadcaster recorder = new RecordingBroadcaster();
        WebSocketPusher pusher = new WebSocketPusher(provider(recorder));
        assertTrue(pusher.broadcast("auth-qr-login:t-1", "{\"status\":\"SCANNED\"}"));
        assertEquals("auth-qr-login:t-1", recorder.topic);
        assertEquals("{\"status\":\"SCANNED\"}", recorder.payload);
    }

    @Test
    void absentBroadcasterDegradesToFalse() {
        WebSocketPusher pusher = new WebSocketPusher(provider(null));
        assertFalse(pusher.sendToUser("1001", Map.of("a", 1)));
        assertFalse(pusher.broadcast("topic", "raw"));
    }

    private static final class RecordingBroadcaster implements WebSocketBroadcaster {

        private String topic;

        private String userId;

        private String payload;

        @Override
        public void broadcast(String topic, String payload) {
            this.topic = topic;
            this.payload = payload;
        }

        @Override
        public void sendToUser(String userId, String payload) {
            this.userId = userId;
            this.payload = payload;
        }

        @Override
        public boolean sendToSession(String sessionId, String payload) {
            return false;
        }
    }
}
