package cn.slainc.web.websocket;

import org.springframework.util.StringUtils;

public final class WebSocketTokenResolver {

    private WebSocketTokenResolver() {
    }

    public static String resolve(WebSocketHandshake handshake, WebSocketProperties.Auth auth) {
        String token = handshake.parameter(auth.getTokenParameter());
        if (!StringUtils.hasText(token)) {
            token = handshake.header(auth.getTokenHeader());
            String prefix = auth.getTokenPrefix();
            if (StringUtils.hasText(token) && StringUtils.hasText(prefix) && token.startsWith(prefix)) {
                token = token.substring(prefix.length());
            }
        }
        return StringUtils.hasText(token) ? token.trim() : null;
    }
}
