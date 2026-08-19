package cn.slainc.web.timeout;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTimeoutWatchTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void countsOverdueRequestOnce() {
        RequestTimeoutWatch watch = watch(Duration.ofSeconds(30));
        String id = watch.begin("GET", "/slow", "127.0.0.1");
        clock.advance(Duration.ofSeconds(31));

        watch.end(id, 200);
        assertEquals(1, watch.overdueTotal());
        assertEquals(0, watch.inFlightCount());

        watch.end(id, 200);
        assertEquals(1, watch.overdueTotal(), "同一请求只应计数一次");
        watch.destroy();
    }

    @Test
    void ignoresRequestWithinThreshold() {
        RequestTimeoutWatch watch = watch(Duration.ofSeconds(30));
        String id = watch.begin("GET", "/fast", "127.0.0.1");
        clock.advance(Duration.ofMillis(10));

        watch.end(id, 200);
        assertEquals(0, watch.overdueTotal());
        watch.destroy();
    }

    @Test
    void sweepsRequestsStillRunning() {
        RequestTimeoutWatch watch = watch(Duration.ofSeconds(30));
        watch.begin("GET", "/stuck", "127.0.0.1");
        clock.advance(Duration.ofSeconds(31));

        assertEquals(1, watch.sweep());
        assertEquals(1, watch.overdueTotal());
        assertEquals(1, watch.overdueRequests().size());
        assertEquals(0, watch.sweep(), "巡检不应重复计数");
        watch.destroy();
    }

    @Test
    void doesNothingWhenDisabled() {
        RequestTimeoutProperties properties = new RequestTimeoutProperties();
        properties.setEnabled(false);
        RequestTimeoutWatch watch = new RequestTimeoutWatch(properties, clock);

        assertNull(watch.begin("GET", "/any", "127.0.0.1"));
        assertEquals(0, watch.sweep());
        assertTrue(watch.overdueRequests().isEmpty());
        watch.destroy();
    }

    private RequestTimeoutWatch watch(Duration threshold) {
        RequestTimeoutProperties properties = new RequestTimeoutProperties();
        properties.setObserveTimeout(threshold);
        return new RequestTimeoutWatch(properties, clock);
    }

    static class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
