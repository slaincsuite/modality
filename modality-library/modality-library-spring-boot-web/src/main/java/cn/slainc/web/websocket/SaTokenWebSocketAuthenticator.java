package cn.slainc.web.websocket;

import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaTokenWebSocketAuthenticator implements WebSocketAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(SaTokenWebSocketAuthenticator.class);

    private final WebSocketProperties properties;

    public SaTokenWebSocketAuthenticator(WebSocketProperties properties) {
        this.properties = properties;
    }

    @Override
    public WebSocketUser authenticate(WebSocketHandshake handshake) {
        String token = WebSocketTokenResolver.resolve(handshake, properties.getAuth());
        if (token == null) {
            log.debug("WebSocket 握手未携带令牌，拒绝建立连接：{}", handshake.uri());
            return null;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                log.debug("Sa-Token 未识别该令牌，拒绝建立连接：{}", handshake.uri());
                return null;
            }
            return new WebSocketUser(String.valueOf(loginId), String.valueOf(loginId));
        } catch (RuntimeException exception) {
            log.warn("Sa-Token 校验 WebSocket 令牌失败，拒绝建立连接：{}", exception.getMessage());
            return null;
        }
    }
}
