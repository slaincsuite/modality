package cn.slainc.sse.handshake;

public interface SseAuthenticator {

    SseUser authenticate(SseHandshake handshake);
}
