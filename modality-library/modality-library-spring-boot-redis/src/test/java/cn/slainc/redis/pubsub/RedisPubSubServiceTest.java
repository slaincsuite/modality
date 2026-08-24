package cn.slainc.redis.pubsub;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.serialize.RedisSerializers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisPubSubServiceTest {

    private static LettuceConnectionFactory subscriberFactory;

    private static LettuceConnectionFactory publisherFactory;

    private static RedisPubSubService pubSubService;

    private static StringRedisTemplate publisherTemplate;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        subscriberFactory = RedisTestSupport.newConnectionFactory();
        publisherFactory = RedisTestSupport.newConnectionFactory();
        publisherTemplate = RedisTestSupport.newStringRedisTemplate(publisherFactory);
        StringRedisTemplate subscriberTemplate = RedisTestSupport.newStringRedisTemplate(subscriberFactory);
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(subscriberFactory);
        container.afterPropertiesSet();
        container.start();
        pubSubService = new RedisPubSubService(subscriberTemplate, container, RedisSerializers.plainMapper());
    }

    @AfterAll
    static void afterAll() {
        if (publisherFactory != null) {
            publisherFactory.destroy();
        }
        if (subscriberFactory != null) {
            subscriberFactory.destroy();
        }
    }

    @Test
    void 订阅频道后能收到独立连接发布的消息() throws Exception {
        String channel = "modality:test:pubsub:" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        List<String> received = new CopyOnWriteArrayList<>();
        MessageListener listener = (message, pattern) -> {
            received.add(new String(message.getBody(), StandardCharsets.UTF_8));
            latch.countDown();
        };
        pubSubService.subscribe(channel, listener);
        assertThat(pubSubService.channelListenerCount(channel)).isEqualTo(1);
        assertThat(pubSubService.isRunning()).isTrue();

        // 先发布一条预热消息，确认订阅真正建立后再验证正文
        for (int i = 0; i < 40 && latch.getCount() > 0; i++) {
            publisherTemplate.convertAndSend(channel, "预热");
            latch.await(100L, TimeUnit.MILLISECONDS);
        }
        assertThat(received).contains("预热");

        CountDownLatch second = new CountDownLatch(1);
        List<String> payloads = new ArrayList<>();
        pubSubService.subscribe(channel, (Message message, byte[] pattern) -> {
            payloads.add(new String(message.getBody(), StandardCharsets.UTF_8));
            second.countDown();
        });
        long subscribers = pubSubService.publish(channel, "正式消息");
        assertThat(subscribers).isPositive();
        assertThat(second.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(payloads).containsExactly("正式消息");
    }

    @Test
    void 发布对象会被序列化为JSON并支持下线() throws Exception {
        String channel = "modality:test:pubsub:" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        List<String> received = new CopyOnWriteArrayList<>();
        MessageListener listener = (message, pattern) -> {
            received.add(new String(message.getBody(), StandardCharsets.UTF_8));
            latch.countDown();
        };
        pubSubService.subscribe(channel, listener);
        for (int i = 0; i < 40 && latch.getCount() > 0; i++) {
            pubSubService.publishObject(channel, Map.of("名称", "模态"));
            latch.await(100L, TimeUnit.MILLISECONDS);
        }
        assertThat(received).anyMatch(text -> text.contains("模态"));

        pubSubService.unsubscribe(channel);
        assertThat(pubSubService.channelListenerCount(channel)).isZero();

        CountDownLatch after = new CountDownLatch(1);
        pubSubService.subscribe(channel, (message, pattern) -> after.countDown());
        pubSubService.publish(channel, "重新订阅");
        assertThat(after.await(5, TimeUnit.SECONDS)).isTrue();
        pubSubService.unsubscribeAll();
    }

    @Test
    void 参数校验与非法发布频道() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pubSubService.publish(null, "内容"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发布频道不能为空");
    }
}
