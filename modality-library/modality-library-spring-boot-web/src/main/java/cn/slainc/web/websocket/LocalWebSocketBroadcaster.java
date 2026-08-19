package cn.slainc.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public class LocalWebSocketBroadcaster implements WebSocketBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LocalWebSocketBroadcaster.class);

    private final WebSocketSessionRegistry registry;

    public LocalWebSocketBroadcaster(WebSocketSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void broadcast(String topic, String payload) {
        for (WebSocketSession session : registry.sessionsOfTopic(topic)) {
            send(session, payload, "主题 " + topic);
        }
    }

    @Override
    public void sendToUser(String userId, String payload) {
        for (WebSocketSession session : registry.sessionsOfUser(userId)) {
            send(session, payload, "用户 " + userId);
        }
    }

    @Override
    public boolean sendToSession(String sessionId, String payload) {
        return send(registry.session(sessionId), payload, "会话 " + sessionId);
    }

    private boolean send(WebSocketSession session, String payload, String target) {
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException exception) {
            log.warn("WebSocket 消息发送失败（{}）：{}", target, exception.getMessage());
            return false;
        }
    }
}
