package cn.slainc.web.websocket;

public interface WebSocketBroadcaster {

    void broadcast(String topic, String payload);

    void sendToUser(String userId, String payload);

    boolean sendToSession(String sessionId, String payload);
}
