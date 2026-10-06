package cn.slainc.web.websocket;

import cn.slainc.utils.json.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;

public class WebSocketPusher {

    private final ObjectProvider<WebSocketBroadcaster> broadcaster;

    public WebSocketPusher(ObjectProvider<WebSocketBroadcaster> broadcaster) {
        this.broadcaster = broadcaster;
    }

    public boolean sendToUser(String userId, Object payload) {
        WebSocketBroadcaster socket = broadcaster.getIfAvailable();
        if (socket == null) {
            return false;
        }
        socket.sendToUser(userId, toJson(payload));
        return true;
    }

    public boolean broadcast(String topic, Object payload) {
        WebSocketBroadcaster socket = broadcaster.getIfAvailable();
        if (socket == null) {
            return false;
        }
        socket.broadcast(topic, toJson(payload));
        return true;
    }

    private String toJson(Object payload) {
        return payload instanceof String text ? text : JsonUtils.toJson(payload);
    }
}
