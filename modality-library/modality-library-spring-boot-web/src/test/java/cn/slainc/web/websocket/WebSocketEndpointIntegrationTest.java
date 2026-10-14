package cn.slainc.web.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = WebSocketEndpointIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"modality.web.websocket.enabled=true",
                "modality.web.websocket.heartbeat-interval=200ms",
                "modality.web.websocket.heartbeat-timeout=5s"})
class WebSocketEndpointIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @LocalServerPort
    int port;

    @Autowired
    WebSocketSessionRegistry registry;

    @Autowired
    WebSocketBroadcaster broadcaster;

    @Test
    void rejectsHandshakeWithoutToken() {
        CompletionException exception = assertThrows(CompletionException.class,
                () -> WebSocketTestClient.connect(port, "", null));

        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class,
                exception.getCause());
        assertEquals(401, handshake.getResponse().statusCode());
    }

    @Test
    void answersPingAfterHandshakeWithToken() throws Exception {
        WebSocketTestClient client = WebSocketTestClient.connect(port, "?token=alice", null);
        try {
            assertFalse(registry.sessionsOfUser("alice").isEmpty());

            client.send("{\"type\":\"ping\"}");
            String reply = client.awaitMessage(Duration.ofSeconds(5));
            assertNotNull(reply, "未收到 pong 响应");
            assertTrue(reply.contains("pong"), reply);

            client.send("ping");
            assertNotNull(client.awaitMessage(Duration.ofSeconds(5)));
        } finally {
            client.close();
        }
    }

    @Test
    void broadcastsToSubscribedSessionsOnly() throws Exception {
        WebSocketTestClient alice = WebSocketTestClient.connect(port, "?token=alice", null);
        WebSocketTestClient bob = WebSocketTestClient.connect(port, "", "Bearer bob");
        try {
            alice.subscribe("news");
            assertTrue(registry.subscriberCount("news") >= 1);

            bob.send("{\"type\":\"ping\"}");
            assertNotNull(bob.awaitMessage(Duration.ofSeconds(5)));

            broadcaster.broadcast("news", "群发消息");

            assertEquals("群发消息", alice.awaitMessage(Duration.ofSeconds(5)));
            assertNull(bob.awaitMessage(Duration.ofMillis(300)), "未订阅的会话不应收到群发消息");
        } finally {
            alice.close();
            bob.close();
        }
    }

    @Test
    void sendsMessageToSingleUser() throws Exception {
        WebSocketTestClient alice = WebSocketTestClient.connect(port, "?token=alice", null);
        WebSocketTestClient bob = WebSocketTestClient.connect(port, "?token=bob", null);
        try {
            awaitUntil(() -> !registry.sessionsOfUser("bob").isEmpty(), Duration.ofSeconds(5));

            broadcaster.sendToUser("bob", "点对点消息");

            assertEquals("点对点消息", bob.awaitMessage(Duration.ofSeconds(5)));
            assertNull(alice.awaitMessage(Duration.ofMillis(300)), "点对点消息不应发给其他用户");
        } finally {
            alice.close();
            bob.close();
        }
    }

    @Test
    void keepsSessionAliveByHeartbeatAndRemovesItAfterClose() throws Exception {
        WebSocketTestClient client = WebSocketTestClient.connect(port, "?token=carol", null);
        try {
            assertFalse(registry.sessionsOfUser("carol").isEmpty());

            Thread.sleep(700);
            assertNull(client.awaitMessage(Duration.ofMillis(100)));
            assertFalse(registry.sessionsOfUser("carol").isEmpty(), "心跳探测期间连接应保持");
        } finally {
            client.close();
        }

        awaitUntil(() -> registry.sessionsOfUser("carol").isEmpty(), Duration.ofSeconds(5));
    }

    private void awaitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), "等待条件超时");
    }
}
