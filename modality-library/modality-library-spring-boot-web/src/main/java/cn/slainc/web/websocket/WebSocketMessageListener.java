package cn.slainc.web.websocket;

import org.springframework.web.socket.WebSocketSession;

public interface WebSocketMessageListener {

    void onMessage(WebSocketUser user, String payload, WebSocketSession session);
}
