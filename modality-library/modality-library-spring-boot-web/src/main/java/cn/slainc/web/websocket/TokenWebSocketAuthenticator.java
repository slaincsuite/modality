package cn.slainc.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenWebSocketAuthenticator implements WebSocketAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(TokenWebSocketAuthenticator.class);

    private final WebSocketProperties properties;

    public TokenWebSocketAuthenticator(WebSocketProperties properties) {
        this.properties = properties;
    }

    @Override
    public WebSocketUser authenticate(WebSocketHandshake handshake) {
        String token = WebSocketTokenResolver.resolve(handshake, properties.getAuth());
        if (token == null) {
            log.debug("WebSocket 握手未携带令牌，拒绝建立连接：{}", handshake.uri());
            return null;
        }
        // 默认实现只校验令牌是否存在，令牌即身份标识，接入方应提供自己的 WebSocketAuthenticator
        return new WebSocketUser(token, token);
    }
}
