package cn.slainc.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ATTRIBUTE = "cn.slainc.web.websocket.user";

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandshakeInterceptor.class);

    private final WebSocketAuthenticator authenticator;

    public WebSocketHandshakeInterceptor(WebSocketAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        WebSocketUser user = authenticate(request, attributes);
        if (user == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ATTRIBUTE, user);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private WebSocketUser authenticate(ServerHttpRequest request, Map<String, Object> attributes) {
        try {
            return authenticator.authenticate(new WebSocketHandshake(request, attributes));
        } catch (RuntimeException exception) {
            log.warn("WebSocket 握手鉴权异常，拒绝建立连接：{}", exception.getMessage());
            return null;
        }
    }
}
