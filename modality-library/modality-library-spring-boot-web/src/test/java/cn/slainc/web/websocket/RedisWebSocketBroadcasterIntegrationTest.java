package cn.slainc.web.websocket;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(classes = RedisWebSocketBroadcasterIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"modality.web.websocket.enabled=true",
                "modality.web.websocket.broadcast=redis",
                "modality.web.websocket.channel=modality:test:websocket:broadcast",
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=6379",
                "spring.data.redis.password=${MODALITY_REDIS_PASSWORD:}"})
class RedisWebSocketBroadcasterIntegrationTest {

    private static final String CHANNEL = "modality:test:websocket:broadcast";

    private static final String REDIS_HOST = "127.0.0.1";

    private static final int REDIS_PORT = 6379;

    private static final String REDIS_PASSWORD = System.getenv().getOrDefault("MODALITY_REDIS_PASSWORD", "");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @LocalServerPort
    int port;

    @Autowired
    WebSocketBroadcaster broadcaster;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedisConnectionFactory connectionFactory;

    @BeforeAll
    static void requireRedis() {
        assumeTrue(reachable(), "本地 127.0.0.1:6379 没有可用的 Redis，已跳过真实 Redis 广播测试");
    }

    @Test
    void usesRedisBroadcasterWhenConfigured() {
        RedisWebSocketBroadcaster redis = assertInstanceOf(RedisWebSocketBroadcaster.class, broadcaster);
        assertEquals(CHANNEL, redis.channel());
        assertNotNull(redis.nodeId());
    }

    @Test
    void deliversMessagePublishedByOtherNode() throws Exception {
        WebSocketTestClient client = WebSocketTestClient.connect(port, "?token=dave", null);
        try {
            client.subscribe("redis-news");

            stringRedisTemplate.convertAndSend(CHANNEL, "{\"type\":\"topic\",\"topic\":\"redis-news\","
                    + "\"payload\":\"来自其他节点的消息\",\"nodeId\":\"other-node\"}");

            assertEquals("来自其他节点的消息", client.awaitMessage(Duration.ofSeconds(5)));
        } finally {
            client.close();
        }
    }

    @Test
    void deliversBroadcastToLocalSubscriber() throws Exception {
        WebSocketTestClient client = WebSocketTestClient.connect(port, "?token=dave", null);
        try {
            client.subscribe("redis-local");

            broadcaster.broadcast("redis-local", "本地直投消息");

            assertEquals("本地直投消息", client.awaitMessage(Duration.ofSeconds(5)));
        } finally {
            client.close();
        }
    }

    @Test
    void publishesBroadcastToRedisChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(connectionFactory);
        listener.addMessageListener((message, pattern) ->
                received.add(new String(message.getBody(), StandardCharsets.UTF_8)), new ChannelTopic(CHANNEL));
        listener.afterPropertiesSet();
        listener.start();
        try {
            broadcaster.broadcast("redis-topic", "广播载荷");

            String envelope = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(envelope, "Redis 频道未收到广播消息");
            assertTrue(envelope.contains("redis-topic"), envelope);
            assertTrue(envelope.contains("广播载荷"), envelope);
            assertTrue(envelope.contains(((RedisWebSocketBroadcaster) broadcaster).nodeId()), envelope);
        } finally {
            listener.destroy();
        }
    }

    private static boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(REDIS_HOST, REDIS_PORT), 500);
            socket.setSoTimeout(800);
            OutputStream output = socket.getOutputStream();
            output.write(("AUTH " + REDIS_PASSWORD + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write("PING\r\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            byte[] buffer = new byte[64];
            int read = socket.getInputStream().read(buffer);
            return read > 0 && new String(buffer, 0, read, StandardCharsets.UTF_8).contains("PONG");
        } catch (IOException exception) {
            return false;
        }
    }
}
