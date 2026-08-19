package cn.slainc.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class WebSocketMessageHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketMessageHandler.class);

    private final WebSocketSessionRegistry registry;

    private final ObjectProvider<WebSocketMessageListener> listeners;

    public WebSocketMessageHandler(WebSocketSessionRegistry registry,
                                   ObjectProvider<WebSocketMessageListener> listeners) {
        this.registry = registry;
        this.listeners = listeners;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketUser user = (WebSocketUser) session.getAttributes()
                .get(WebSocketHandshakeInterceptor.USER_ATTRIBUTE);
        registry.register(user, session);
        log.info("WebSocket 连接建立 sessionId={} 用户={}", session.getId(),
                user == null ? "匿名" : user.userId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        registry.touchActive(session.getId());
        WebSocketCommand command = WebSocketCommand.parse(message.getPayload());
        if (command == null) {
            return;
        }
        switch (command.type()) {
            case WebSocketCommand.PING -> session.sendMessage(new TextMessage(WebSocketCommand.reply("pong", null, null)));
            case WebSocketCommand.PONG -> registry.touchActive(session.getId());
            case WebSocketCommand.SUBSCRIBE -> {
                registry.subscribe(session.getId(), command.topic());
                session.sendMessage(new TextMessage(
                        WebSocketCommand.reply("subscribed", command.topic(), null)));
            }
            case WebSocketCommand.UNSUBSCRIBE -> {
                registry.unsubscribe(session.getId(), command.topic());
                session.sendMessage(new TextMessage(
                        WebSocketCommand.reply("unsubscribed", command.topic(), null)));
            }
            default -> dispatch(session, message.getPayload());
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        registry.touchActive(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输异常 sessionId={}：{}", session.getId(), exception.getMessage());
        registry.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session.getId());
        log.info("WebSocket 连接关闭 sessionId={} 状态码={} 原因={}", session.getId(), status.getCode(),
                status.getReason());
    }

    private void dispatch(WebSocketSession session, String payload) {
        WebSocketUser user = registry.user(session.getId());
        listeners.orderedStream().forEach(listener -> {
            try {
                listener.onMessage(user, payload, session);
            } catch (RuntimeException exception) {
                log.warn("WebSocket 消息监听器处理失败 sessionId={}：{}", session.getId(), exception.getMessage());
            }
        });
    }
}
