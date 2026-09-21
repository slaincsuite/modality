package cn.slainc.redis.stream;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisStreamTemplateTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static RedisStreamTemplate streamTemplate;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        RedisProperties properties = new RedisProperties();
        properties.getStream().setBatchSize(10);
        properties.getStream().setMaxRetry(3);
        streamTemplate = new RedisStreamTemplate(stringRedisTemplate, properties);
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void produceConsumeAckPipeline() {
        String stream = "modality:test:stream:" + UUID.randomUUID();
        String group = "验收组";
        String consumer = "消费者一";

        String id = streamTemplate.produce(stream, "订单已创建");
        assertThat(id).isNotBlank();
        assertThat(streamTemplate.size(stream)).isEqualTo(1L);

        assertThat(streamTemplate.createGroup(stream, group)).isEqualTo("OK");
        assertThat(streamTemplate.ensureGroup(stream, group)).isFalse();

        List<StreamRecord> records = streamTemplate.consume(stream, group, consumer);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getPayload()).isEqualTo("订单已创建");
        assertThat(records.get(0).getId()).isEqualTo(id);
        assertThat(records.get(0).getStream()).isEqualTo(stream);
        assertThat(records.get(0).toString()).contains("流消息");

        assertThat(streamTemplate.pending(stream, group)).hasSize(1);
        assertThat(streamTemplate.ack(stream, group, id)).isEqualTo(1L);
        assertThat(streamTemplate.pending(stream, group)).isEmpty();

        assertThat(streamTemplate.deleteGroup(stream, group)).isTrue();
        assertThat(streamTemplate.delete(stream, id)).isEqualTo(1L);
        assertThat(streamTemplate.size(stream)).isZero();
    }

    @Test
    void deadLetterAfterMaxRetries() {
        String stream = "modality:test:stream:retry:" + UUID.randomUUID();
        String group = "重试组";
        String consumer = "消费者二";
        streamTemplate.produce(stream, "易失败消息");
        streamTemplate.ensureGroup(stream, group);

        String deadLetter = streamTemplate.deadLetterStream(stream);
        assertThat(deadLetter).isEqualTo(stream + properties().getStream().getDeadLetterSuffix());

        int round = 0;
        StreamRetryResult result = null;
        while (round < 5) {
            List<StreamRecord> records = streamTemplate.consume(stream, group, consumer);
            if (records.isEmpty()) {
                break;
            }
            result = streamTemplate.retry(stream, group, records.get(0));
            round++;
            if (result == StreamRetryResult.DEAD_LETTERED) {
                break;
            }
        }
        assertThat(result).isEqualTo(StreamRetryResult.DEAD_LETTERED);
        assertThat(streamTemplate.size(deadLetter)).isEqualTo(1L);
        assertThat(streamTemplate.retry(stream, group, null)).isEqualTo(StreamRetryResult.MISSING);
    }

    @Test
    void trimBatchAckAndBlankNameValidation() {
        String stream = "modality:test:stream:trim:" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            streamTemplate.produce(stream, "消息" + i);
        }
        assertThat(streamTemplate.size(stream)).isEqualTo(5L);
        streamTemplate.trim(stream, 2L);
        assertThat(streamTemplate.size(stream)).isLessThanOrEqualTo(3L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> streamTemplate.produce("  ", "内容"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redis 流的名称不能为空");
    }

    private static RedisProperties properties() {
        RedisProperties properties = new RedisProperties();
        return properties;
    }
}
