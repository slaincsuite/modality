package cn.slainc.web.websocket;

public interface WebSocketAuthenticator {

    WebSocketUser authenticate(WebSocketHandshake handshake);
}
