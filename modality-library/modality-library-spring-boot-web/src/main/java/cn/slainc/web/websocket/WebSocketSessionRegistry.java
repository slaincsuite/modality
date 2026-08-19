package cn.slainc.web.websocket;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketSessionRegistry {

    private static final int SEND_TIME_LIMIT_MILLIS = 5000;

    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final Map<String, WebSocketUser> users = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> sessionIdsByUser = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> sessionIdsByTopic = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> topicsBySession = new ConcurrentHashMap<>();

    private final Map<String, Long> lastActiveAt = new ConcurrentHashMap<>();

    public WebSocketSession register(WebSocketUser user, WebSocketSession session) {
        WebSocketSession concurrent = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS,
                BUFFER_SIZE_LIMIT_BYTES);
        String sessionId = concurrent.getId();
        sessions.put(sessionId, concurrent);
        lastActiveAt.put(sessionId, System.currentTimeMillis());
        if (user != null) {
            users.put(sessionId, user);
            sessionIdsByUser.computeIfAbsent(user.userId(), key -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }
        return concurrent;
    }

    public WebSocketSession remove(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        WebSocketSession session = sessions.remove(sessionId);
        lastActiveAt.remove(sessionId);
        WebSocketUser user = users.remove(sessionId);
        if (user != null) {
            removeFromIndex(sessionIdsByUser, user.userId(), sessionId);
        }
        Set<String> topics = topicsBySession.remove(sessionId);
        if (topics != null) {
            for (String topic : topics) {
                removeFromIndex(sessionIdsByTopic, topic, sessionId);
            }
        }
        return session;
    }

    public WebSocketSession session(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    public WebSocketUser user(String sessionId) {
        return sessionId == null ? null : users.get(sessionId);
    }

    public List<WebSocketSession> sessions() {
        return new ArrayList<>(sessions.values());
    }

    public List<WebSocketSession> sessionsOfUser(String userId) {
        List<WebSocketSession> result = new ArrayList<>();
        for (String sessionId : sessionIds(userId)) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null) {
                result.add(session);
            }
        }
        return result;
    }

    public List<WebSocketSession> sessionsOfTopic(String topic) {
        List<WebSocketSession> result = new ArrayList<>();
        for (String sessionId : sessionIdsOfTopic(topic)) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null) {
                result.add(session);
            }
        }
        return result;
    }

    public Set<String> sessionIdsOfTopic(String topic) {
        Set<String> sessionIds = topic == null ? null : sessionIdsByTopic.get(topic);
        return sessionIds == null ? Set.of() : Set.copyOf(sessionIds);
    }

    public Set<String> topicsOfSession(String sessionId) {
        Set<String> topics = sessionId == null ? null : topicsBySession.get(sessionId);
        return topics == null ? Set.of() : Set.copyOf(topics);
    }

    public int subscriberCount(String topic) {
        return sessionIdsOfTopic(topic).size();
    }

    public void subscribe(String sessionId, String topic) {
        if (sessionId == null || topic == null || topic.isBlank()) {
            return;
        }
        sessionIdsByTopic.computeIfAbsent(topic, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
        topicsBySession.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(topic);
    }

    public void unsubscribe(String sessionId, String topic) {
        if (sessionId == null || topic == null) {
            return;
        }
        removeFromIndex(sessionIdsByTopic, topic, sessionId);
        removeFromIndex(topicsBySession, sessionId, topic);
    }

    public void touchActive(String sessionId) {
        if (sessionId != null && sessions.containsKey(sessionId)) {
            lastActiveAt.put(sessionId, System.currentTimeMillis());
        }
    }

    public long lastActiveAt(String sessionId) {
        Long value = sessionId == null ? null : lastActiveAt.get(sessionId);
        return value == null ? 0L : value;
    }

    public int size() {
        return sessions.size();
    }

    private Set<String> sessionIds(String userId) {
        Set<String> sessionIds = userId == null ? null : sessionIdsByUser.get(userId);
        return sessionIds == null ? Set.of() : Set.copyOf(sessionIds);
    }

    private void removeFromIndex(Map<String, Set<String>> index, String key, String value) {
        Set<String> values = index.get(key);
        if (values != null && values.remove(value) && values.isEmpty()) {
            index.remove(key, values);
        }
    }
}
