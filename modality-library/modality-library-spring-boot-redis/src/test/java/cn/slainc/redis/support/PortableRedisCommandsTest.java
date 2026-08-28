package cn.slainc.redis.support;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.monitor.SlowLogEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortableRedisCommandsTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate template;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        template = RedisTestSupport.newStringRedisTemplate(factory);
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("MEMORY USAGE 通过 Lua 返回整数，不依赖客户端原生接口")
    void memoryUsageWorksThroughLua() {
        String key = "portable:mem:" + UUID.randomUUID();
        template.opsForValue().set(key, "abcdefghijklmnopqrstuvwxyz");
        try {
            Long usage = PortableRedisCommands.memoryUsage(template, key);
            assertThat(usage).isNotNull().isPositive();
        } finally {
            template.delete(key);
        }
    }

    @Test
    @DisplayName("不存在的键返回 null 而不是抛异常")
    void memoryUsageReturnsNullForMissingKey() {
        assertThat(PortableRedisCommands.memoryUsage(template, "portable:missing:" + UUID.randomUUID())).isNull();
    }

    @Test
    @DisplayName("SLOWLOG 通过 Lua 返回可解析的条目列表")
    void slowlogWorksThroughLua() {
        List<SlowLogEntry> entries = PortableRedisCommands.slowlog(template, 5);
        assertThat(entries).isNotNull();
        for (SlowLogEntry entry : entries) {
            assertThat(entry.getId()).isNotNegative();
            assertThat(entry.getDurationMicros()).isNotNegative();
        }
    }

    @Test
    @DisplayName("RedisMonitor 复用可移植实现读取慢查询")
    void monitorSlowlogUsesPortablePath() {
        cn.slainc.redis.autoconfigure.RedisProperties properties = new cn.slainc.redis.autoconfigure.RedisProperties();
        cn.slainc.redis.monitor.RedisMonitor monitor = new cn.slainc.redis.monitor.RedisMonitor(template, properties);
        assertThat(monitor.slowlog(3)).isNotNull();
    }

    @Test
    @DisplayName("RedisKeyManager 复用可移植实现读取内存占用")
    void keyManagerUsesPortablePath() {
        cn.slainc.redis.key.RedisKeyManager keyManager = new cn.slainc.redis.key.RedisKeyManager(template, null,
                new cn.slainc.redis.autoconfigure.RedisProperties());
        String key = "portable:keymgr:" + UUID.randomUUID();
        template.opsForValue().set(key, "value");
        try {
            assertThat(keyManager.memoryUsage(key)).isNotNull().isPositive();
        } finally {
            template.delete(key);
        }
    }
}
