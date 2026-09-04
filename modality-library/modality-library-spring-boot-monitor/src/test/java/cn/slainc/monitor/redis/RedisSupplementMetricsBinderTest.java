package cn.slainc.monitor.redis;

import cn.slainc.monitor.support.MonitorTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.monitor.RedisMetricsSnapshot;
import cn.slainc.redis.monitor.RedisMonitor;
import cn.slainc.redis.monitor.SlowLogEntry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSupplementMetricsBinderTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("复用 RedisMonitor 快照补齐命中、淘汰、阻塞与慢查询指标")
    void shouldBindSupplementGauges() {
        StubRedisMonitor monitor = new StubRedisMonitor();
        monitor.snapshot = RedisSupplementMetricsBinderTest.snapshot(120L, 30L, 7L, 2L, 3L);
        monitor.slowlog = List.of(new SlowLogEntry(1L, 0L, 5_000L, List.of("GET")),
                new SlowLogEntry(2L, 0L, 250_000L, List.of("KEYS")),
                new SlowLogEntry(3L, 0L, 400_000L, List.of("HGETALL")));
        RedisSupplementMetricsBinder binder = new RedisSupplementMetricsBinder(monitor,
                MonitorTestSupport.metricsTemplate(registry), "default", 60_000L, 10, 100_000L);
        binder.bindTo(registry);
        assertThat(gauge("modality.redis.keyspace.hits")).isEqualTo(120d);
        assertThat(gauge("modality.redis.keyspace.misses")).isEqualTo(30d);
        assertThat(gauge("modality.redis.expired.keys")).isEqualTo(7d);
        assertThat(gauge("modality.redis.evicted.keys")).isEqualTo(2d);
        assertThat(gauge("modality.redis.clients.blocked")).isEqualTo(3d);
        assertThat(gauge("modality.redis.slowlog.size")).isEqualTo(2d);
        assertThat(gauge("modality.redis.slowlog.max.micros")).isEqualTo(400_000d);
    }

    @Test
    @DisplayName("缓存窗口内不重复访问 Redis")
    void shouldCacheSnapshot() {
        StubRedisMonitor monitor = new StubRedisMonitor();
        monitor.snapshot = RedisSupplementMetricsBinderTest.snapshot(10L, 1L, 0L, 0L, 0L);
        monitor.slowlog = List.of();
        RedisSupplementMetricsBinder binder = new RedisSupplementMetricsBinder(monitor,
                MonitorTestSupport.metricsTemplate(registry), "default", 60_000L, 10, 100_000L);
        binder.bindTo(registry);
        assertThat(gauge("modality.redis.keyspace.hits")).isEqualTo(10d);
        monitor.snapshot = RedisSupplementMetricsBinderTest.snapshot(999L, 1L, 0L, 0L, 0L);
        assertThat(gauge("modality.redis.keyspace.hits")).isEqualTo(10d);
        assertThat(monitor.snapshotCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis 不可用时降级为 0 且不抛异常")
    void shouldDegradeWhenRedisUnavailable() {
        StubRedisMonitor monitor = new StubRedisMonitor();
        monitor.failure = new IllegalStateException("Redis 连接失败");
        RedisSupplementMetricsBinder binder = new RedisSupplementMetricsBinder(monitor,
                MonitorTestSupport.metricsTemplate(registry), "default", 0L, 10, 100_000L);
        binder.bindTo(registry);
        assertThat(gauge("modality.redis.keyspace.hits")).isZero();
        assertThat(gauge("modality.redis.slowlog.size")).isZero();
    }

    private double gauge(String name) {
        return registry.get(name).tag("instance", "default").gauge().value();
    }

    private static RedisMetricsSnapshot snapshot(long hits, long misses, long expired, long evicted,
                                                 long blocked) {
        return new RedisMetricsSnapshot(hits, misses, expired, evicted, 5L, blocked, 1024L, 100L, 10L, 42L,
                System.currentTimeMillis());
    }

    private static class StubRedisMonitor extends RedisMonitor {

        private RedisMetricsSnapshot snapshot = RedisSupplementMetricsBinderTest.snapshot(0L, 0L, 0L, 0L, 0L);

        private List<SlowLogEntry> slowlog = List.of();

        private RuntimeException failure;

        private int snapshotCalls;

        private StubRedisMonitor() {
            super(new StringRedisTemplate(), new RedisProperties());
        }

        @Override
        public RedisMetricsSnapshot snapshot() {
            snapshotCalls++;
            if (failure != null) {
                throw failure;
            }
            return snapshot;
        }

        @Override
        public List<SlowLogEntry> slowlog(int count) {
            return slowlog;
        }
    }
}
