package cn.slainc.sse.auth;

import cn.slainc.sse.autoconfigure.SseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenSseAuthenticator implements SseAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(TokenSseAuthenticator.class);

    private final SseProperties properties;

    public TokenSseAuthenticator(SseProperties properties) {
        this.properties = properties;
    }

    @Override
    public SseUser authenticate(SseHandshake handshake) {
        String token = SseTokenResolver.resolve(handshake, properties.getAuth());
        if (token == null) {
            log.debug("SSE 订阅未携带令牌，拒绝建立连接：{}", handshake.uri());
            return null;
        }
        // 默认实现把令牌本身当作用户编号，接入方应提供自己的 SseAuthenticator
        try {
            return new SseUser(Long.parseLong(token), token);
        } catch (NumberFormatException exception) {
            log.debug("SSE 订阅令牌不是合法的用户编号，拒绝建立连接：{}", token);
            return null;
        }
    }
}
