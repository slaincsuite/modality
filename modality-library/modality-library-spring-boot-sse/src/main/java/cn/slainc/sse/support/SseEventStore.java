package cn.slainc.sse.support;

import cn.slainc.utils.string.StringUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class SseEventStore {

    private static final int MAX_KEYS = 10_000;

    private static final Duration KEY_IDLE_TTL = Duration.ofMinutes(30);

    private final Cache<String, Buffer> events;

    private final int capacity;

    public SseEventStore(int capacity) {
        this(capacity, MAX_KEYS, KEY_IDLE_TTL);
    }

    public SseEventStore(int capacity, int maxKeys, Duration keyIdleTtl) {
        this.capacity = Math.max(capacity, 1);
        Duration idle = keyIdleTtl == null || keyIdleTtl.isZero() || keyIdleTtl.isNegative()
                ? KEY_IDLE_TTL : keyIdleTtl;
        this.events = Caffeine.newBuilder()
                .maximumSize(maxKeys <= 0 ? MAX_KEYS : maxKeys)
                .expireAfterAccess(idle)
                .build();
    }

    public void append(String key, SseEvent event) {
        if (StringUtils.isBlank(key) || event == null) {
            return;
        }
        events.get(key, ignored -> new Buffer()).add(event, capacity);
    }

    public List<SseEvent> eventsAfter(String key, String lastEventId) {
        if (StringUtils.isBlank(key) || lastEventId == null) {
            return List.of();
        }
        Buffer buffer = events.getIfPresent(key);
        return buffer == null ? List.of() : buffer.after(lastEventId);
    }

    public boolean contains(String key, String eventId) {
        if (StringUtils.isBlank(key) || eventId == null) {
            return false;
        }
        Buffer buffer = events.getIfPresent(key);
        return buffer != null && buffer.contains(eventId);
    }

    public int size(String key) {
        if (StringUtils.isBlank(key)) {
            return 0;
        }
        Buffer buffer = events.getIfPresent(key);
        return buffer == null ? 0 : buffer.size();
    }

    public int size() {
        int total = 0;
        for (Buffer buffer : events.asMap().values()) {
            total += buffer.size();
        }
        return total;
    }

    public int keys() {
        events.cleanUp();
        return (int) events.estimatedSize();
    }

    public void clear(String key) {
        if (StringUtils.isNotBlank(key)) {
            events.invalidate(key);
        }
    }

    public void clear() {
        events.invalidateAll();
    }

    private static final class Buffer {

        private final ConcurrentLinkedDeque<SseEvent> queue = new ConcurrentLinkedDeque<>();

        private final AtomicInteger size = new AtomicInteger();

        void add(SseEvent event, int capacity) {
            queue.addLast(event);
            if (size.incrementAndGet() > capacity) {
                if (queue.pollFirst() != null) {
                    size.decrementAndGet();
                }
            }
        }

        List<SseEvent> after(String lastEventId) {
            List<SseEvent> result = new ArrayList<>();
            boolean matched = false;
            for (SseEvent event : queue) {
                if (matched) {
                    result.add(event);
                    continue;
                }
                if (lastEventId.equals(event.id())) {
                    matched = true;
                }
            }
            return result;
        }

        boolean contains(String eventId) {
            for (SseEvent event : queue) {
                if (eventId.equals(event.id())) {
                    return true;
                }
            }
            return false;
        }

        int size() {
            return size.get();
        }
    }
}
