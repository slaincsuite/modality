package cn.slainc.sse.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventStoreTest {

    private final SseEventStore store = new SseEventStore(2);

    @Test
    void shouldReplayEventsAfterLastEventIdWithinTheSameKey() {
        store.append("1", new SseEvent("1", "a", "A", LocalDateTime.now()));
        store.append("1", new SseEvent("2", "b", "B", LocalDateTime.now()));
        store.append("1", new SseEvent("3", "c", "C", LocalDateTime.now()));

        assertThat(store.size("1")).isEqualTo(2);
        assertThat(store.eventsAfter("1", "2")).extracting(SseEvent::id).containsExactly("3");
        assertThat(store.eventsAfter("1", "3")).isEmpty();
        assertThat(store.eventsAfter("1", null)).isEmpty();
        assertThat(store.eventsAfter(null, "2")).isEmpty();
    }

    @Test
    void shouldKeepBuffersIsolatedPerKey() {
        store.append("1", new SseEvent("a-1", "a", "A", LocalDateTime.now()));
        store.append("2", new SseEvent("b-1", "b", "B", LocalDateTime.now()));

        assertThat(store.eventsAfter("1", "a-1")).isEmpty();
        assertThat(store.eventsAfter("1", "b-1")).isEmpty();
        assertThat(store.contains("2", "b-1")).isTrue();
        assertThat(store.contains("2", "a-1")).isFalse();
        assertThat(store.size()).isEqualTo(2);
        assertThat(store.keys()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreInvalidAppendsAndClearKeys() {
        store.append(null, new SseEvent("1", "a", "A", LocalDateTime.now()));
        store.append("1", null);
        assertThat(store.size()).isZero();

        store.append("1", new SseEvent("1", "a", "A", LocalDateTime.now()));
        store.clear("1");
        assertThat(store.size("1")).isZero();
        assertThat(store.size("missing")).isZero();
        assertThat(store.contains("1", null)).isFalse();

        store.append("1", new SseEvent("1", "a", "A", LocalDateTime.now()));
        store.clear();
        assertThat(store.size()).isZero();
    }

    @Test
    void shouldRejectBlankKeys() {
        store.append("  ", new SseEvent("1", "a", "A", LocalDateTime.now()));
        assertThat(store.size()).isZero();
    }
}
