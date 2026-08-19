package cn.slainc.web.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSessionRegistryTest {

    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry();

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void indexesSessionByUserAndTopic() {
        registry.register(new WebSocketUser("u1", "张三"), session("s1"));
        registry.register(new WebSocketUser("u1", "张三"), session("s2"));
        registry.register(new WebSocketUser("u2", "李四"), session("s3"));

        assertEquals(3, registry.size());
        assertEquals(2, registry.sessionsOfUser("u1").size());
        assertEquals("u1", registry.user("s1").userId());

        registry.subscribe("s1", "news");
        registry.subscribe("s2", "news");
        registry.subscribe("s2", "sports");

        assertEquals(Set.of("s1", "s2"), registry.sessionIdsOfTopic("news"));
        assertEquals(2, registry.sessionsOfTopic("news").size());
        assertEquals(Set.of("news", "sports"), registry.topicsOfSession("s2"));

        registry.unsubscribe("s1", "news");
        assertEquals(Set.of("s2"), registry.sessionIdsOfTopic("news"));
        assertTrue(registry.topicsOfSession("s1").isEmpty());
    }

    @Test
    void removesSessionAndAllIndexes() {
        registry.register(new WebSocketUser("u1", "张三"), session("s1"));
        registry.subscribe("s1", "news");

        WebSocketSession removed = registry.remove("s1");

        assertNotNull(removed);
        assertEquals(0, registry.size());
        assertNull(registry.user("s1"));
        assertTrue(registry.sessionsOfUser("u1").isEmpty());
        assertTrue(registry.sessionIdsOfTopic("news").isEmpty());
        assertNull(registry.remove("s1"));
    }

    @Test
    void tracksLastActiveTime() throws Exception {
        registry.register(null, session("s1"));
        long registered = registry.lastActiveAt("s1");
        assertTrue(registered > 0);

        Thread.sleep(5);
        registry.touchActive("s1");
        assertTrue(registry.lastActiveAt("s1") > registered);
        assertEquals(0, registry.lastActiveAt("unknown"));
    }

    @Test
    void supportsConcurrentRegistrationAndRemoval() throws Exception {
        int threads = 8;
        int operations = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int index = 0; index < threads; index++) {
            final int worker = index;
            executor.execute(() -> {
                try {
                    for (int operation = 0; operation < operations; operation++) {
                        String sessionId = "s-" + worker + "-" + operation;
                        registry.register(new WebSocketUser("u" + worker, "用户" + worker), session(sessionId));
                        registry.subscribe(sessionId, "topic-" + (operation % 4));
                        registry.touchActive(sessionId);
                        registry.remove(sessionId);
                    }
                } catch (RuntimeException exception) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(0, failures.get());
        assertEquals(0, registry.size());
        assertTrue(registry.sessionsOfUser("u0").isEmpty());
        for (int topic = 0; topic < 4; topic++) {
            assertTrue(registry.sessionIdsOfTopic("topic-" + topic).isEmpty());
        }
    }
}
