package cn.slainc.modules.job.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeRegistryTest {

    private static final String PREFIX = "modality:job:console";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final NodeRegistry registry = new NodeRegistry(redisTemplate, PREFIX, Duration.ofMinutes(1), "node-1");

    @BeforeEach
    void stubValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldSendHeartbeatAndGoOffline() {
        registry.heartbeat();
        verify(valueOperations).set(eq(PREFIX + ":node:node-1"), any(), eq(Duration.ofMinutes(1)));
        registry.offline();
        verify(redisTemplate).delete(PREFIX + ":node:node-1");
        assertThat(registry.nodeId()).isEqualTo("node-1");
    }

    @Test
    void shouldListClusterNodesWithScanInsteadOfKeys() {
        when(redisTemplate.scan(any(ScanOptions.class)))
                .thenAnswer(invocation -> cursor(PREFIX + ":node:node-1", PREFIX + ":node:node-2"));
        when(valueOperations.get(PREFIX + ":node:node-1")).thenReturn("2026-10-05T10:00:00");

        assertThat(registry.nodes()).containsExactlyInAnyOrder("node-1", "node-2");
        assertThat(registry.nodeStatus()).containsKey("node-1");
        verify(redisTemplate, atLeastOnce()).scan(any(ScanOptions.class));
    }

    // 每次扫描返回一个新的只读游标桩，避免多次调用共享消费同一份连续桩
    @SuppressWarnings("unchecked")
    private static Cursor<String> cursor(String... keys) {
        java.util.Iterator<String> iterator = java.util.List.of(keys).iterator();
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(cursor.next()).thenAnswer(invocation -> iterator.next());
        return cursor;
    }

    @Test
    void shouldDeregisterNodeOnShutdownAndLogFailures() {
        registry.shutdown();
        verify(redisTemplate).delete(PREFIX + ":node:node-1");

        doThrow(new IllegalStateException("redis 不可用")).when(redisTemplate).delete(anyString());
        registry.shutdown();
    }

    @Test
    void shouldDegradeToEmptyRegistryWithoutRedis() {
        NodeRegistry noRedis = new NodeRegistry(null, PREFIX, Duration.ofMinutes(1), "node-1");

        assertThatCodeQuietly(() -> noRedis.heartbeat());
        assertThatCodeQuietly(noRedis::offline);
        assertThat(noRedis.nodes()).isEmpty();
        assertThat(noRedis.nodeStatus()).isEmpty();
        assertThat(noRedis.nodeId()).isEqualTo("node-1");
    }

    private static void assertThatCodeQuietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            throw new AssertionError("无 Redis 时应当静默降级", e);
        }
    }
}
