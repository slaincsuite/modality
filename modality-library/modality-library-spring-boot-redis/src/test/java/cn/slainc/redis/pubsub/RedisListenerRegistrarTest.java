package cn.slainc.redis.pubsub;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.serialize.RedisSerializers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisListenerRegistrarTest {

    private static LettuceConnectionFactory subscriberFactory;

    private static LettuceConnectionFactory publisherFactory;

    private static StringRedisTemplate publisherTemplate;

    private static RedisPubSubService pubSubService;

    private static SampleListener sampleListener;

    private static RedisListenerRegistrar registrar;

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

        sampleListener = new SampleListener();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("sampleListener", sampleListener);
        registrar = new RedisListenerRegistrar(pubSubService, beanFactory, new RedisProperties());
        registrar.afterSingletonsInstantiated();
    }

    @AfterAll
    static void afterAll() {
        if (registrar != null) {
            registrar.destroy();
        }
        if (publisherFactory != null) {
            publisherFactory.destroy();
        }
        if (subscriberFactory != null) {
            subscriberFactory.destroy();
        }
    }

    @Test
    void 注解监听方法自动注册并接收消息() throws Exception {
        String channel = "modality:test:listener:fixed";
        sampleListener.reset(channel);

        for (int i = 0; i < 40 && sampleListener.latch.getCount() > 0; i++) {
            publisherTemplate.convertAndSend(channel, "预热");
            sampleListener.latch.await(100L, TimeUnit.MILLISECONDS);
        }
        assertThat(sampleListener.latch.getCount()).isZero();
        assertThat(sampleListener.body).isEqualTo("预热");
        assertThat(sampleListener.channel).isEqualTo(channel);

        sampleListener.reset(channel);
        publisherTemplate.convertAndSend(channel, "正文内容");
        assertThat(sampleListener.latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sampleListener.body).isEqualTo("正文内容");
    }

    @Test
    void 按通道参数名注入频道名并支持模式订阅() throws Exception {
        String channel = "modality:test:listener:" + UUID.randomUUID();
        sampleListener.resetPattern(channel);
        for (int i = 0; i < 40 && sampleListener.latch.getCount() > 0; i++) {
            publisherTemplate.convertAndSend(channel, "模式消息");
            sampleListener.latch.await(100L, TimeUnit.MILLISECONDS);
        }
        assertThat(sampleListener.latch.getCount()).isZero();
        assertThat(sampleListener.body).isEqualTo("模式消息");
        assertThat(sampleListener.channel).isEqualTo(channel);
    }

    static class SampleListener {

        private volatile CountDownLatch latch = new CountDownLatch(1);

        private volatile String body;

        private volatile String channel;

        private volatile String patternChannel;

        private volatile boolean usePattern;

        void reset(String channel) {
            this.channel = null;
            this.body = null;
            this.usePattern = false;
            this.patternChannel = channel;
            this.latch = new CountDownLatch(1);
        }

        void resetPattern(String channel) {
            reset(channel);
            this.usePattern = true;
        }

        @RedisListener(patterns = "modality:test:listener:*")
        public void onPatternMessage(String message, String topic) {
            if (usePattern && patternChannel != null && patternChannel.equals(topic)) {
                this.body = message;
                this.channel = topic;
                latch.countDown();
            }
        }

        @RedisListener(channels = "modality:test:listener:fixed")
        public void onFixedMessage(String message, String channel) {
            this.body = message;
            this.channel = channel;
            latch.countDown();
        }
    }
}
