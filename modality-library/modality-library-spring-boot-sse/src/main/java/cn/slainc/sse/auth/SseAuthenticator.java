package cn.slainc.sse.auth;

public interface SseAuthenticator {

    SseUser authenticate(SseHandshake handshake);
}
