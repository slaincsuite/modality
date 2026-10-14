package cn.slainc.sse.cluster;

import cn.slainc.redis.pubsub.RedisPubSubService;
import cn.slainc.sse.SseRedisTestSupport;
import cn.slainc.sse.api.impl.SseServiceImpl;
import cn.slainc.sse.autoconfigure.SseProperties;
import cn.slainc.sse.support.SseConnectionRegistry;
import cn.slainc.sse.support.SseEventStore;
import cn.slainc.sse.support.SseMetrics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class SseClusterRedisIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;

    private static RedisMessageListenerContainer container;

    private static RedisPubSubService pubSubService;

    @BeforeAll
    static void assumeRedisAndStart() {
        SseRedisTestSupport.assumeRedis();
        connectionFactory = SseRedisTestSupport.newConnectionFactory();
        StringRedisTemplate template = SseRedisTestSupport.newStringRedisTemplate(connectionFactory);
        container = SseRedisTestSupport.newMessageListenerContainer(connectionFactory);
        pubSubService = SseRedisTestSupport.newPubSubService(template, container);
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldRelayEventsFromOneNodeToAnother() throws Exception {
        SseProperties properties = new SseProperties();
        properties.getCluster().setTopic("modality:sse:test:" + System.nanoTime());
        String topic = properties.getCluster().getTopic();

        RecordingSseService receiver = new RecordingSseService();
        SseClusterSubscriber subscriber = new SseClusterSubscriber(pubSubService, receiver, topic, "node-b");
        subscriber.afterPropertiesSet();

        SseServiceImpl sender = new SseServiceImpl(new SseConnectionRegistry(),
                new SseEventStore(properties.getReplayBufferSize()), properties, new SseMetrics(),
                new RedisSseEventRelay(pubSubService, topic, "node-a"));

        receiver.subscribe(42L);
        sender.send(42L, "notice", "跨节点 hello");

        assertThat(awaitFrames(receiver.emitter(0))).contains("跨节点 hello");

        subscriber.destroy();
    }

    private static String awaitFrames(RecordingSseEmitter emitter) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        String joined = String.join("", emitter.frames());
        while (System.currentTimeMillis() < deadline && !joined.contains("跨节点 hello")) {
            Thread.sleep(50L);
            joined = String.join("", emitter.frames());
        }
        return joined;
    }

    private static class RecordingSseService extends SseServiceImpl {

        private final List<RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();

        RecordingSseService() {
            super(new SseConnectionRegistry(), new SseEventStore(100), new SseProperties());
        }

        @Override
        protected SseEmitter createEmitter() {
            RecordingSseEmitter emitter = new RecordingSseEmitter();
            emitters.add(emitter);
            return emitter;
        }

        RecordingSseEmitter emitter(int index) {
            return emitters.get(index);
        }
    }

    private static class RecordingSseEmitter extends SseEmitter {

        private final List<String> frames = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            StringBuilder frame = new StringBuilder();
            for (DataWithMediaType data : builder.build()) {
                frame.append(data.getData());
            }
            frames.add(frame.toString());
        }

        List<String> frames() {
            return frames;
        }
    }
}
