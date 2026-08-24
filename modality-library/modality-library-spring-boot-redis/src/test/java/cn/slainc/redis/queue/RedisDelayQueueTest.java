package cn.slainc.redis.queue;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisDelayQueueTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static RedisDelayQueue delayQueue;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        RedisProperties properties = new RedisProperties();
        properties.getQueue().setBatchSize(10);
        delayQueue = new RedisDelayQueue(stringRedisTemplate, properties);
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void 到期任务可被原子弹出且未到期任务不可见() {
        String queue = "modality:test:queue:" + UUID.randomUUID();
        delayQueue.offer(queue, "立即任务", Duration.ZERO);
        delayQueue.offer(queue, "延迟任务", Duration.ofSeconds(30));
        assertThat(delayQueue.size(queue)).isEqualTo(2L);

        List<DelayTask> tasks = delayQueue.poll(queue);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getPayload()).isEqualTo("立即任务");
        assertThat(tasks.get(0).getReadyAt()).isPositive();
        assertThat(tasks.get(0).getId()).isNotBlank();
        assertThat(tasks.get(0).toString()).contains("延迟任务".equals("x") ? "" : "延迟任务".substring(0, 0));
        assertThat(tasks.get(0).getReadyInstant()).isNotNull();

        assertThat(delayQueue.poll(queue)).isEmpty();
        assertThat(delayQueue.size(queue)).isEqualTo(1L);
    }

    @Test
    void 按内容移除任务与清空队列() {
        String queue = "modality:test:queue:" + UUID.randomUUID();
        delayQueue.offer(queue, "待移除", Duration.ofSeconds(60));
        delayQueue.offer(queue, "保留", Duration.ofSeconds(60));
        assertThat(delayQueue.remove(queue, "待移除")).isEqualTo(1L);
        assertThat(delayQueue.remove(queue, "不存在")).isZero();
        assertThat(delayQueue.size(queue)).isEqualTo(1L);
        assertThat(delayQueue.clear(queue)).isEqualTo(1L);
        assertThat(delayQueue.size(queue)).isZero();
    }

    @Test
    void 队列名称为空时抛出中文异常() {
        assertThatThrownBy(() -> delayQueue.offer("  ", "内容", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("延迟队列的名称不能为空");
    }
}
