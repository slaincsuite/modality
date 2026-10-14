package cn.slainc.sse.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.sse.api.SseService;
import cn.slainc.sse.handshake.SseAuthenticator;
import cn.slainc.sse.handshake.SseHandshake;
import cn.slainc.sse.handshake.SseUser;
import cn.slainc.sse.autoconfigure.SseProperties;
import cn.slainc.sse.cluster.SseEventRelay;
import cn.slainc.sse.support.SseConnectionRegistry;
import cn.slainc.sse.support.SseEvent;
import cn.slainc.sse.support.SseEventStore;
import cn.slainc.sse.support.SseMetrics;
import cn.slainc.utils.random.UuidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SseServiceImpl implements SseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SseServiceImpl.class);

    private final SseConnectionRegistry registry;

    private final SseEventStore eventStore;

    private final SseProperties properties;

    private final SseMetrics metrics;

    private final SseEventRelay relay;

    private final SseAuthenticator authenticator;

    public SseServiceImpl(SseConnectionRegistry registry, SseEventStore eventStore, SseProperties properties) {
        this(registry, eventStore, properties, new SseMetrics(), SseEventRelay.local());
    }

    public SseServiceImpl(SseConnectionRegistry registry, SseEventStore eventStore, SseProperties properties,
                          SseMetrics metrics, SseEventRelay relay) {
        this(registry, eventStore, properties, metrics, relay, null);
    }

    public SseServiceImpl(SseConnectionRegistry registry, SseEventStore eventStore, SseProperties properties,
                          SseMetrics metrics, SseEventRelay relay, SseAuthenticator authenticator) {
        this.registry = registry;
        this.eventStore = eventStore;
        this.properties = properties;
        this.metrics = metrics == null ? new SseMetrics() : metrics;
        this.relay = relay == null ? SseEventRelay.local() : relay;
        this.authenticator = authenticator;
    }

    @Override
    public SseEmitter subscribe(Long userId) {
        return subscribe(userId, null);
    }

    @Override
    public SseEmitter subscribe(Long userId, String lastEventId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "SSE 订阅需要已认证的用户");
        }
        SseEmitter emitter = createEmitter();
        String connectionId = UuidUtils.uuidCompact();
        SseConnectionRegistry.Connection connection =
                new SseConnectionRegistry.Connection(connectionId, userId, emitter, LocalDateTime.now());
        if (!canConnect(userId)
                || !registry.register(connectionId, userId, emitter, properties.getMaxConnectionsPerUser())) {
            metrics.connectionRejected();
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "用户 " + userId + " 的 SSE 连接数已达上限");
        }
        metrics.connectionOpened();
        registry.send(connection, CONNECT_EVENT, connectionId, connectionId, reconnectTimeMillis());
        replay(userId, connection, lastEventId);
        return emitter;
    }

    @Override
    public SseEmitter subscribe(SseHandshake handshake) {
        return subscribe(handshake, handshake == null ? null : handshake.header(LAST_EVENT_ID_HEADER));
    }

    @Override
    public SseEmitter subscribe(SseHandshake handshake, String lastEventId) {
        SseUser user = authenticate(handshake);
        if (user == null || user.userId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "SSE 订阅需要已认证的用户");
        }
        return subscribe(user.userId(), lastEventId);
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(properties.getTimeout().toMillis());
    }

    private SseUser authenticate(SseHandshake handshake) {
        if (handshake == null || authenticator == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "SSE 订阅缺少认证器，无法识别用户身份");
        }
        try {
            return authenticator.authenticate(handshake);
        } catch (RuntimeException exception) {
            LOGGER.warn("SSE 订阅认证失败，已拒绝建立连接：{}", exception.getMessage());
            return null;
        }
    }

    @Override
    public int send(Long userId, String eventName, Object data) {
        if (userId == null) {
            return 0;
        }
        SseEvent event = new SseEvent(newEventId(), eventName, data, LocalDateTime.now());
        int sent = dispatchLocal(userId, event);
        relay.publish(userId, event);
        if (relay.remote()) {
            metrics.eventRelayed();
        }
        return sent;
    }

    @Override
    public int deliver(Long userId, SseEvent event) {
        if (userId == null || event == null) {
            return 0;
        }
        int sent = dispatchLocal(userId, event);
        metrics.eventReceived();
        return sent;
    }

    @Override
    public int broadcast(String eventName, Object data) {
        Collection<SseConnectionRegistry.Connection> connections = registry.all();
        if (connections.isEmpty()) {
            return 0;
        }
        SseEvent event = new SseEvent(newEventId(), eventName, data, LocalDateTime.now());
        Set<Long> recipients = new LinkedHashSet<>();
        for (SseConnectionRegistry.Connection connection : connections) {
            if (connection.userId() != null) {
                recipients.add(connection.userId());
            }
        }
        recipients.forEach(userId -> eventStore.append(String.valueOf(userId), event));
        int sent = 0;
        for (SseConnectionRegistry.Connection connection : connections) {
            if (registry.send(connection, event.name(), event.data(), event.id())) {
                sent++;
            }
        }
        metrics.eventSent(sent);
        return sent;
    }

    @Override
    public int heartbeat() {
        Collection<SseConnectionRegistry.Connection> connections = registry.all();
        if (connections.isEmpty()) {
            return 0;
        }
        String data = LocalDateTime.now().toString();
        int sent = 0;
        for (SseConnectionRegistry.Connection connection : connections) {
            if (registry.send(connection, properties.getHeartbeatEvent(), data, null)) {
                sent++;
            }
        }
        metrics.heartbeat(sent);
        return sent;
    }

    @Override
    public int connections() {
        return registry.count();
    }

    @Override
    public int connections(Long userId) {
        return registry.countByUser(userId);
    }

    @Override
    public boolean canConnect(Long userId) {
        if (userId == null) {
            return false;
        }
        int max = properties.getMaxConnectionsPerUser();
        return max <= 0 || registry.countByUser(userId) < max;
    }

    @Override
    public SseEventStore eventStore() {
        return eventStore;
    }

    @Override
    public SseMetrics metrics() {
        return metrics;
    }

    @Override
    public void disconnect(String connectionId) {
        registry.remove(connectionId);
    }

    private int replay(Long userId, SseConnectionRegistry.Connection connection, String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()
                || !eventStore.contains(String.valueOf(userId), lastEventId)) {
            return 0;
        }
        int replayed = 0;
        for (SseEvent event : eventStore.eventsAfter(String.valueOf(userId), lastEventId)) {
            if (registry.send(connection, event.name(), event.data(), event.id())) {
                replayed++;
            }
        }
        metrics.eventReplayed(replayed);
        return replayed;
    }

    private int dispatchLocal(Long userId, SseEvent event) {
        List<SseConnectionRegistry.Connection> connections = registry.byUser(userId);
        if (connections.isEmpty()) {
            return 0;
        }
        eventStore.append(String.valueOf(userId), event);
        int sent = 0;
        for (SseConnectionRegistry.Connection connection : connections) {
            if (registry.send(connection, event.name(), event.data(), event.id())) {
                sent++;
            }
        }
        metrics.eventSent(sent);
        return sent;
    }

    private Long reconnectTimeMillis() {
        return properties.getReconnectTime() == null ? null : properties.getReconnectTime().toMillis();
    }

    private String newEventId() {
        return UuidUtils.uuidCompact();
    }
}
