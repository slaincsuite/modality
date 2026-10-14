package cn.slainc.sse.auth;

import cn.slainc.sse.autoconfigure.SseProperties;
import cn.slainc.utils.string.StringUtils;

public final class SseTokenResolver {

    private SseTokenResolver() {
    }

    public static String resolve(SseHandshake handshake, SseProperties.Auth auth) {
        String token = handshake.parameter(auth.getTokenParameter());
        if (StringUtils.isBlank(token)) {
            token = handshake.header(auth.getTokenHeader());
            String prefix = auth.getTokenPrefix();
            if (StringUtils.isNotBlank(token) && StringUtils.isNotBlank(prefix) && token.startsWith(prefix)) {
                token = token.substring(prefix.length());
            }
        }
        return StringUtils.isNotBlank(token) ? token.trim() : null;
    }
}
