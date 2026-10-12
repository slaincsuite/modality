package cn.slainc.modules.auth.store;

import cn.slainc.modules.auth.qrcode.QrLoginStatus;
import cn.slainc.modules.auth.qrcode.QrLoginTicket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTicketStoreTest {

    private static LettuceConnectionFactory redisFactory;

    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void startRedis() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        configuration.setDatabase(15);
        configuration.setPassword(RedisPassword.of(System.getenv().getOrDefault("MODALITY_REDIS_PASSWORD", "")));
        redisFactory = new LettuceConnectionFactory(configuration);
        redisFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(redisFactory);
    }

    @AfterAll
    static void stopRedis() {
        if (redisFactory != null) {
            redisFactory.destroy();
        }
    }

    private static QrLoginTicket ticket(String id) {
        return QrLoginTicket.waiting(id, 60);
    }

    @Test
    void memoryModeSaveFindDelete() {
        AuthTicketStore<QrLoginTicket> store = AuthTicketStore.of(null, "test:qr:",
                QrLoginTicket::getTicketId, QrLoginTicket.class);
        store.save(ticket("m-1"), 60);
        assertTrue(store.find("m-1").isPresent());
        store.delete("m-1");
        assertTrue(store.find("m-1").isEmpty());
    }

    @Test
    void memoryModeConsumeIsOneShot() {
        AuthTicketStore<QrLoginTicket> store = AuthTicketStore.of(null, "test:qr:",
                QrLoginTicket::getTicketId, QrLoginTicket.class);
        store.save(ticket("m-2"), 60);
        assertTrue(store.consume("m-2").isPresent());
        assertTrue(store.consume("m-2").isEmpty());
        assertTrue(store.find("m-2").isEmpty());
    }

    @Test
    void memoryModeCompareAndSetRespectsGuard() {
        AuthTicketStore<QrLoginTicket> store = AuthTicketStore.of(null, "test:qr:",
                QrLoginTicket::getTicketId, QrLoginTicket.class);
        store.save(ticket("m-cas"), 60);
        QrLoginTicket confirmed = ticket("m-cas");
        confirmed.setStatus(QrLoginStatus.CONFIRMED);
        assertFalse(store.compareAndSet("m-cas", current -> current.getStatus() == QrLoginStatus.SCANNED,
                confirmed, 60));
        assertTrue(store.compareAndSet("m-cas", current -> current.getStatus() == QrLoginStatus.WAITING,
                confirmed, 60));
        assertEquals(QrLoginStatus.CONFIRMED, store.find("m-cas").orElseThrow().getStatus());
    }

    @Test
    void memoryModeCompareAndSetMissesUnknownTicket() {
        AuthTicketStore<QrLoginTicket> store = AuthTicketStore.of(null, "test:qr:",
                QrLoginTicket::getTicketId, QrLoginTicket.class);
        assertFalse(store.compareAndSet("absent", current -> true, ticket("absent"), 60));
    }

    @Test
    void memoryModeEntryExpiresByTtl() throws InterruptedException {
        AuthTicketStore<QrLoginTicket> store = AuthTicketStore.of(null, "test:qr:",
                QrLoginTicket::getTicketId, QrLoginTicket.class);
        store.save(ticket("m-3"), 1);
        TimeUnit.MILLISECONDS.sleep(1200);
        assertTrue(store.find("m-3").isEmpty());
        assertTrue(store.consume("m-3").isEmpty());
    }

    @Test
    void redisModeSaveFindDelete() {
        AuthTicketStore<QrLoginTicket> store = AuthTicketStore.of(redisTemplate, "test:qr:",
                QrLoginTicket::getTicketId, QrLoginTicket.class);
        store.save(ticket("r-1"), 60);
        Optional<QrLoginTicket> found = store.find("r-1");
        assertTrue(found.isPresent());
        assertEquals("r-1", found.get().getTicketId());
        store.delete("r-1");
        assertTrue(store.find("r-1").isEmpty());
    }

    @Test
    void redisModeConsumeIsAtomicAndOneShot() throws Exception {
        AuthTicketStore<QrLoginTicket> store = AuthTicketStore.of(redisTemplate, "test:qr:",
                QrLoginTicket::getTicketId, QrLoginTicket.class);
        String ticketId = "r-2-" + System.currentTimeMillis();
        store.save(ticket(ticketId), 60);
        int threads = 8;
        ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger winners = new AtomicInteger();
            List<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return store.consume(ticketId).isPresent();
                }));
            }
            ready.await();
            start.countDown();
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    winners.incrementAndGet();
                }
            }
            assertEquals(1, winners.get());
        } finally {
            executor.shutdownNow();
        }
        assertTrue(store.find(ticketId).isEmpty());
    }
}
