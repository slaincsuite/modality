package cn.slainc.redis.monitor;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisMonitorTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static RedisMonitor monitor;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        RedisProperties properties = new RedisProperties();
        properties.getMonitor().setInstance("验收实例");
        monitor = new RedisMonitor(stringRedisTemplate, properties);
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void 分节读取INFO信息() {
        Map<String, String> memory = monitor.memoryInfo();
        assertThat(memory).containsKey("used_memory");

        Map<String, String> stats = monitor.statsInfo();
        assertThat(stats).containsKeys("keyspace_hits", "keyspace_misses", "total_commands_processed");

        assertThat(monitor.persistenceInfo()).containsKey("loading");
        assertThat(monitor.clientsInfo()).containsKey("connected_clients");
        assertThat(monitor.replicationInfo()).containsKey("role");
        assertThat(monitor.info()).isNotEmpty();
        assertThat(monitor.info("server")).containsKey("redis_version");
    }

    @Test
    void 指标快照包含命中率连接数与QPS() {
        stringRedisTemplate.opsForValue().set("modality:test:monitor:key", "值");
        stringRedisTemplate.opsForValue().get("modality:test:monitor:key");
        stringRedisTemplate.delete("modality:test:monitor:key");

        RedisMetricsSnapshot snapshot = monitor.snapshot();
        assertThat(snapshot.getConnectedClients()).isPositive();
        assertThat(snapshot.getUsedMemoryBytes()).isPositive();
        assertThat(snapshot.getTotalCommandsProcessed()).isPositive();
        assertThat(snapshot.getHits() + snapshot.getMisses()).isPositive();
        assertThat(snapshot.getHitRate()).isBetween(0d, 1d);
        assertThat(snapshot.getDbSize()).isPositive();
        assertThat(snapshot.getTimestamp()).isPositive();
        assertThat(snapshot.toString()).contains("Redis 指标");
    }

    @Test
    void 慢查询日志与阈值配置可读() {
        assertThat(monitor.slowlog(10)).isNotNull();
        assertThat(monitor.dbSize()).isPositive();
        assertThat(monitor.slowlogThresholdMicros()).isEqualTo(100000L);
        assertThat(new SlowLogEntry(1L, 1_700_000_000L, 12_000L, java.util.List.of("GET", "键")).toString())
                .contains("慢查询");
    }

    @Test
    void 快照对象字段可单独访问() {
        RedisMetricsSnapshot snapshot = new RedisMetricsSnapshot(8L, 2L, 1L, 0L, 3L, 0L, 1024L, 100L, 5L, 9L,
                1_700_000_000_000L);
        assertThat(snapshot.getHitRate()).isEqualTo(0.8d);
        assertThat(snapshot.getExpiredKeys()).isEqualTo(1L);
        assertThat(snapshot.getEvictedKeys()).isZero();
        assertThat(snapshot.getBlockedClients()).isZero();
        assertThat(snapshot.getInstantaneousOpsPerSec()).isEqualTo(5L);
    }
}
