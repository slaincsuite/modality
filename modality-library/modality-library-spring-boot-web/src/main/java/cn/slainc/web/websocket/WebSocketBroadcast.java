package cn.slainc.web.websocket;

import cn.slainc.utils.json.JsonUtils;

public record WebSocketBroadcast(String type, String topic, String userId, String sessionId, String payload,
                                 String nodeId) {

    public static final String TYPE_TOPIC = "topic";

    public static final String TYPE_USER = "user";

    public static final String TYPE_SESSION = "session";

    public static WebSocketBroadcast topic(String topic, String payload, String nodeId) {
        return new WebSocketBroadcast(TYPE_TOPIC, topic, null, null, payload, nodeId);
    }

    public static WebSocketBroadcast user(String userId, String payload, String nodeId) {
        return new WebSocketBroadcast(TYPE_USER, null, userId, null, payload, nodeId);
    }

    public static WebSocketBroadcast session(String sessionId, String payload, String nodeId) {
        return new WebSocketBroadcast(TYPE_SESSION, null, null, sessionId, payload, nodeId);
    }

    public static WebSocketBroadcast parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.parse(json, WebSocketBroadcast.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
