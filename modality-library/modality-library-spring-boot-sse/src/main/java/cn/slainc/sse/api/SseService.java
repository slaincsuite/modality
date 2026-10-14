package cn.slainc.sse.api;

import cn.slainc.sse.handshake.SseHandshake;
import cn.slainc.sse.support.SseEvent;
import cn.slainc.sse.support.SseEventStore;
import cn.slainc.sse.support.SseMetrics;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseService {

    String CONNECT_EVENT = "connect";

    String LAST_EVENT_ID_HEADER = "Last-Event-ID";

    SseEmitter subscribe(Long userId);

    SseEmitter subscribe(Long userId, String lastEventId);

    SseEmitter subscribe(SseHandshake handshake);

    SseEmitter subscribe(SseHandshake handshake, String lastEventId);

    int send(Long userId, String eventName, Object data);

    int deliver(Long userId, SseEvent event);

    int broadcast(String eventName, Object data);

    int heartbeat();

    int connections();

    int connections(Long userId);

    boolean canConnect(Long userId);

    SseEventStore eventStore();

    SseMetrics metrics();

    void disconnect(String connectionId);
}
