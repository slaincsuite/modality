package cn.slainc.sse.support;

import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SseConnectionRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(SseConnectionRegistry.class);

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    private final Map<Long, Set<String>> userIndex = new ConcurrentHashMap<>();

    public boolean register(String connectionId, Long userId, SseEmitter emitter, int maxConnectionsPerUser) {
        if (StringUtils.isBlank(connectionId) || emitter == null) {
            return false;
        }
        if (userId != null && !admit(userId, connectionId, maxConnectionsPerUser)) {
            return false;
        }
        Connection connection = new Connection(connectionId, userId, emitter, LocalDateTime.now());
        connections.put(connectionId, connection);
        emitter.onCompletion(() -> unregister(connectionId));
        emitter.onTimeout(() -> {
            unregister(connectionId);
            emitter.complete();
        });
        emitter.onError(error -> unregister(connectionId));
        return true;
    }

    private boolean admit(Long userId, String connectionId, int maxConnectionsPerUser) {
        boolean[] admitted = {false};
        userIndex.compute(userId, (key, current) -> {
            Set<String> connectionsOfUser = current == null ? ConcurrentHashMap.newKeySet() : current;
            if (maxConnectionsPerUser > 0 && connectionsOfUser.size() >= maxConnectionsPerUser) {
                return connectionsOfUser;
            }
            connectionsOfUser.add(connectionId);
            admitted[0] = true;
            return connectionsOfUser;
        });
        return admitted[0];
    }

    public void remove(String connectionId) {
        Connection connection = unregister(connectionId);
        if (connection != null) {
            try {
                connection.emitter().complete();
            } catch (RuntimeException e) {
                LOGGER.debug("关闭 SSE 连接失败：connectionId={}", connectionId, e);
            }
        }
    }

    public Connection unregister(String connectionId) {
        if (StringUtils.isBlank(connectionId)) {
            return null;
        }
        Connection connection = connections.remove(connectionId);
        if (connection == null || connection.userId() == null) {
            return connection;
        }
        userIndex.computeIfPresent(connection.userId(), (key, connectionsOfUser) -> {
            connectionsOfUser.remove(connectionId);
            return connectionsOfUser.isEmpty() ? null : connectionsOfUser;
        });
        return connection;
    }

    public int count() {
        return connections.size();
    }

    public int countByUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        Set<String> connectionsOfUser = userIndex.get(userId);
        return connectionsOfUser == null ? 0 : connectionsOfUser.size();
    }

    public Collection<Connection> all() {
        return new ArrayList<>(connections.values());
    }

    public List<Connection> byUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Set<String> connectionsOfUser = userIndex.get(userId);
        if (connectionsOfUser == null || connectionsOfUser.isEmpty()) {
            return List.of();
        }
        List<Connection> result = new ArrayList<>(connectionsOfUser.size());
        for (String connectionId : connectionsOfUser) {
            Connection connection = connections.get(connectionId);
            if (connection != null) {
                result.add(connection);
            }
        }
        return result;
    }

    public boolean send(Connection connection, String eventName, Object data, String eventId) {
        return send(connection, eventName, data, eventId, null);
    }

    public boolean send(Connection connection, String eventName, Object data, String eventId, Long reconnectTimeMillis) {
        if (connection == null) {
            return false;
        }
        SseEmitter.SseEventBuilder builder = SseEmitter.event().name(eventName);
        if (StringUtils.isNotBlank(eventId)) {
            builder.id(eventId);
        }
        if (reconnectTimeMillis != null && reconnectTimeMillis > 0) {
            builder.reconnectTime(reconnectTimeMillis.longValue());
        }
        builder.data(data);
        return send(connection, builder);
    }

    public boolean send(Connection connection, SseEmitter.SseEventBuilder builder) {
        if (connection == null || builder == null) {
            return false;
        }
        try {
            connection.emitter().send(builder);
            return true;
        } catch (IOException | IllegalStateException e) {
            LOGGER.debug("SSE 事件发送失败，已移除连接：connectionId={}，原因={}", connection.connectionId(), e.getMessage());
            remove(connection.connectionId());
            return false;
        }
    }

    public record Connection(String connectionId, Long userId, SseEmitter emitter, LocalDateTime createTime) {
    }
}
