package cn.slainc.monitor.redis;

import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.redis.monitor.RedisMetricsSnapshot;
import cn.slainc.redis.monitor.RedisMonitor;
import cn.slainc.redis.monitor.SlowLogEntry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RedisSupplementMetricsBinder implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(RedisSupplementMetricsBinder.class);

    private final RedisMonitor redisMonitor;

    private final MetricsTemplate metrics;

    private final String instance;

    private final long cacheMillis;

    private final int slowlogCount;

    private final long slowlogThresholdMicros;

    private volatile RedisMetricsSnapshot snapshot;

    private volatile long slowlogSize;

    private volatile long slowlogMaxMicros;

    private volatile long sampledAt;

    private volatile boolean warned;

    public RedisSupplementMetricsBinder(RedisMonitor redisMonitor, MetricsTemplate metrics, String instance,
                                        long cacheMillis, int slowlogCount, long slowlogThresholdMicros) {
        if (redisMonitor == null) {
            throw new IllegalArgumentException("RedisMonitor 不能为空");
        }
        this.redisMonitor = redisMonitor;
        this.metrics = metrics;
        this.instance = instance == null || instance.isBlank() ? "default" : instance;
        this.cacheMillis = Math.max(cacheMillis, 0L);
        this.slowlogCount = Math.max(slowlogCount, 1);
        this.slowlogThresholdMicros = slowlogThresholdMicros;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        metrics.gauge("modality.redis.keyspace.hits", "Redis 累计命中键数量", this,
                RedisSupplementMetricsBinder::hits, "instance", instance);
        metrics.gauge("modality.redis.keyspace.misses", "Redis 累计未命中键数量", this,
                RedisSupplementMetricsBinder::misses, "instance", instance);
        metrics.gauge("modality.redis.expired.keys", "Redis 累计过期键数量", this,
                RedisSupplementMetricsBinder::expiredKeys, "instance", instance);
        metrics.gauge("modality.redis.evicted.keys", "Redis 累计淘汰键数量", this,
                RedisSupplementMetricsBinder::evictedKeys, "instance", instance);
        metrics.gauge("modality.redis.clients.blocked", "Redis 阻塞客户端数量", this,
                RedisSupplementMetricsBinder::blockedClients, "instance", instance);
        metrics.gauge("modality.redis.slowlog.size", "Redis 慢查询条数（最近一次采样）", this,
                RedisSupplementMetricsBinder::slowlogSize, "instance", instance);
        metrics.gauge("modality.redis.slowlog.max.micros", "Redis 慢查询最大耗时（微秒）", this,
                RedisSupplementMetricsBinder::slowlogMaxMicros, "instance", instance);
    }

    private void sampleIfStale() {
        if (System.currentTimeMillis() - sampledAt < cacheMillis) {
            return;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - sampledAt < cacheMillis) {
                return;
            }
            try {
                snapshot = redisMonitor.snapshot();
                List<SlowLogEntry> entries = redisMonitor.slowlog(slowlogCount);
                long size = 0L;
                long maxMicros = 0L;
                for (SlowLogEntry entry : entries) {
                    if (entry.getDurationMicros() >= slowlogThresholdMicros) {
                        size++;
                    }
                    maxMicros = Math.max(maxMicros, entry.getDurationMicros());
                }
                slowlogSize = size;
                slowlogMaxMicros = maxMicros;
            } catch (RuntimeException exception) {
                if (!warned) {
                    warned = true;
                    log.warn("Redis 补充指标采集失败，将保持上一次取值: {}", exception.getMessage());
                }
            } finally {
                sampledAt = System.currentTimeMillis();
            }
        }
    }

    private double hits() {
        return value() == null ? 0d : value().getHits();
    }

    private double misses() {
        return value() == null ? 0d : value().getMisses();
    }

    private double expiredKeys() {
        return value() == null ? 0d : value().getExpiredKeys();
    }

    private double evictedKeys() {
        return value() == null ? 0d : value().getEvictedKeys();
    }

    private double blockedClients() {
        return value() == null ? 0d : value().getBlockedClients();
    }

    private double slowlogSize() {
        return slowlogSize;
    }

    private double slowlogMaxMicros() {
        return slowlogMaxMicros;
    }

    private RedisMetricsSnapshot value() {
        sampleIfStale();
        return snapshot;
    }
}
