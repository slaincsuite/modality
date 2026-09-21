package cn.slainc.idempotent.store;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LocalIdempotentStoreTest {

    private final LocalIdempotentStore store = new LocalIdempotentStore();

    @Test
    void concurrentAcquireHasSingleWinner() throws Exception {
        String key = newKey();
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                if (store.tryAcquire(key, Duration.ofSeconds(30))) {
                    winners.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        assertThat(winners.get()).as("本地存储的占位必须原子").isEqualTo(1);
    }

    @Test
    void placeholderAcquirableAfterRelease() {
        String key = newKey();
        assertThat(store.tryAcquire(key, Duration.ofSeconds(30))).isTrue();
        assertThat(store.tryAcquire(key, Duration.ofSeconds(30))).isFalse();
        store.release(key);
        assertThat(store.tryAcquire(key, Duration.ofSeconds(30))).isTrue();
    }

    @Test
    void pendingHoldsAndCompletedReturnsResult() {
        String key = newKey();
        store.tryAcquire(key, Duration.ofSeconds(30));
        assertThat(store.findRecord(key)).isEmpty();
        store.saveRecord(key, new IdempotentRecord(IdempotentState.SUCCESS, String.class.getName(), "\"首次结果\"",
                System.currentTimeMillis()), Duration.ofSeconds(30));
        Optional<IdempotentRecord> record = store.findRecord(key);
        assertThat(record).isPresent();
        assertThat(record.get().hasPayload()).isTrue();
        assertThat(record.get().payload()).isEqualTo("\"首次结果\"");
    }

    @Test
    void tokenConsumedOnlyOnce() {
        String token = "token-" + UUID.randomUUID();
        store.saveToken(token, "1001", Duration.ofSeconds(30));
        assertThat(store.consumeToken(token)).isEqualTo("1001");
        assertThat(store.consumeToken(token)).isNull();
    }

    @Test
    void failureCountAccumulatesInWindowAndClearsAfter() throws Exception {
        String key = newKey();
        assertThat(store.increaseFailure(key, Duration.ofMillis(300))).isEqualTo(1L);
        assertThat(store.increaseFailure(key, Duration.ofMillis(300))).isEqualTo(2L);
        assertThat(store.failureCount(key)).isEqualTo(2L);
        store.clearFailure(key);
        assertThat(store.failureCount(key)).isZero();
        store.increaseFailure(key, Duration.ofMillis(200));
        Thread.sleep(320L);
        assertThat(store.failureCount(key)).as("窗口过期后失败计数应清零").isZero();
    }

    @Test
    void placeholderAcquirableAfterWindowExpiry() throws Exception {
        String key = newKey();
        assertThat(store.tryAcquire(key, Duration.ofMillis(150))).isTrue();
        Thread.sleep(300L);
        assertThat(store.tryAcquire(key, Duration.ofMillis(150))).isTrue();
    }

    private static String newKey() {
        return "local:" + UUID.randomUUID();
    }
}
