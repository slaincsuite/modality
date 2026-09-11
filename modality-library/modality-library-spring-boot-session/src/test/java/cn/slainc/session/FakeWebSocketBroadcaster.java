package cn.slainc.session;

import cn.slainc.web.websocket.WebSocketBroadcaster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeWebSocketBroadcaster implements WebSocketBroadcaster {

    private final List<String[]> userMessages = new CopyOnWriteArrayList<>();

    private final List<String[]> broadcastMessages = new CopyOnWriteArrayList<>();

    @Override
    public void broadcast(String topic, String payload) {
        broadcastMessages.add(new String[]{topic, payload});
    }

    @Override
    public void sendToUser(String userId, String payload) {
        userMessages.add(new String[]{userId, payload});
    }

    @Override
    public boolean sendToSession(String sessionId, String payload) {
        return true;
    }

    public List<String[]> userMessages() {
        return new ArrayList<>(userMessages);
    }

    public List<String[]> broadcastMessages() {
        return new ArrayList<>(broadcastMessages);
    }

    public void clear() {
        userMessages.clear();
        broadcastMessages.clear();
    }
}
