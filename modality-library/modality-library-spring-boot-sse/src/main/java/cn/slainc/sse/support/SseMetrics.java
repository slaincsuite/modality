package cn.slainc.sse.support;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class SseMetrics {

    public static final String METRIC_CONNECTIONS = "modality.sse.connections";

    public static final String METRIC_EVENTS = "modality.sse.events";

    public static final String METRIC_HEARTBEATS = "modality.sse.heartbeats";

    private final LongAdder connectionsOpened = new LongAdder();

    private final LongAdder connectionsRejected = new LongAdder();

    private final LongAdder eventsSent = new LongAdder();

    private final LongAdder eventsReplayed = new LongAdder();

    private final LongAdder eventsRelayed = new LongAdder();

    private final LongAdder eventsReceived = new LongAdder();

    private final LongAdder heartbeats = new LongAdder();

    public SseMetrics() {
        this(null);
    }

    public SseMetrics(MeterRegistry registry) {
        if (registry == null) {
            return;
        }
        counter(registry, METRIC_CONNECTIONS, connectionsOpened, "result", "opened");
        counter(registry, METRIC_CONNECTIONS, connectionsRejected, "result", "rejected");
        counter(registry, METRIC_EVENTS, eventsSent, "type", "sent");
        counter(registry, METRIC_EVENTS, eventsReplayed, "type", "replayed");
        counter(registry, METRIC_EVENTS, eventsRelayed, "type", "relayed");
        counter(registry, METRIC_EVENTS, eventsReceived, "type", "received");
        counter(registry, METRIC_HEARTBEATS, heartbeats, "type", "heartbeat");
    }

    private static void counter(MeterRegistry registry, String name, LongAdder adder, String tagKey, String tagValue) {
        FunctionCounter.builder(name, adder, LongAdder::sum)
                .description("SSE 服务指标")
                .tag(tagKey, tagValue)
                .register(registry);
    }

    public void connectionOpened() {
        connectionsOpened.increment();
    }

    public void connectionRejected() {
        connectionsRejected.increment();
    }

    public void eventSent(int count) {
        if (count > 0) {
            eventsSent.add(count);
        }
    }

    public void eventReplayed(int count) {
        if (count > 0) {
            eventsReplayed.add(count);
        }
    }

    public void eventRelayed() {
        eventsRelayed.increment();
    }

    public void eventReceived() {
        eventsReceived.increment();
    }

    public void heartbeat(int count) {
        if (count > 0) {
            heartbeats.add(count);
        }
    }

    public long connectionsOpened() {
        return connectionsOpened.sum();
    }

    public long connectionsRejected() {
        return connectionsRejected.sum();
    }

    public long eventsSent() {
        return eventsSent.sum();
    }

    public long eventsReplayed() {
        return eventsReplayed.sum();
    }

    public long eventsRelayed() {
        return eventsRelayed.sum();
    }

    public long eventsReceived() {
        return eventsReceived.sum();
    }

    public long heartbeats() {
        return heartbeats.sum();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        snapshot.put("connectionsOpened", connectionsOpened());
        snapshot.put("connectionsRejected", connectionsRejected());
        snapshot.put("eventsSent", eventsSent());
        snapshot.put("eventsReplayed", eventsReplayed());
        snapshot.put("eventsRelayed", eventsRelayed());
        snapshot.put("eventsReceived", eventsReceived());
        snapshot.put("heartbeats", heartbeats());
        return snapshot;
    }
}
