package cn.slainc.redis.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

public class RedisMonitorMetricsBinder implements MeterBinder {

    private final RedisMonitor redisMonitor;

    private final String instance;

    public RedisMonitorMetricsBinder(RedisMonitor redisMonitor, String instance) {
        if (redisMonitor == null) {
            throw new IllegalArgumentException("RedisMonitor 不能为空");
        }
        this.redisMonitor = redisMonitor;
        this.instance = instance == null || instance.isBlank() ? "default" : instance;
    }

    public RedisMonitor getRedisMonitor() {
        return redisMonitor;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("modality.redis.hit.rate", redisMonitor, monitor -> monitor.snapshot().getHitRate())
                .tags(Tags.of("instance", instance))
                .description("Redis 缓存命中率")
                .register(registry);
        Gauge.builder("modality.redis.commands.per.second", redisMonitor,
                        monitor -> monitor.snapshot().getInstantaneousOpsPerSec())
                .tags(Tags.of("instance", instance))
                .description("Redis 瞬时每秒命令数")
                .register(registry);
        Gauge.builder("modality.redis.clients.connected", redisMonitor,
                        monitor -> monitor.snapshot().getConnectedClients())
                .tags(Tags.of("instance", instance))
                .description("Redis 当前连接数")
                .register(registry);
        Gauge.builder("modality.redis.memory.used.bytes", redisMonitor,
                        monitor -> monitor.snapshot().getUsedMemoryBytes())
                .tags(Tags.of("instance", instance))
                .description("Redis 已用内存字节数")
                .register(registry);
        Gauge.builder("modality.redis.keys.total", redisMonitor, monitor -> monitor.snapshot().getDbSize())
                .tags(Tags.of("instance", instance))
                .description("Redis 当前库键总数")
                .register(registry);
    }
}
