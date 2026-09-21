package cn.slainc.idempotent.store;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.idempotent.IdempotentRedisTestSupport;
import cn.slainc.idempotent.support.IdempotentKeys;
import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedisIdempotentStoreTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate redisTemplate;

    private static RedisIdempotentStore store;

    @BeforeAll
    static void beforeAll() {
        IdempotentRedisTestSupport.requireRedis();
        factory = IdempotentRedisTestSupport.newConnectionFactory();
        redisTemplate = IdempotentRedisTestSupport.newStringRedisTemplate(factory);
        store = new RedisIdempotentStore(redisTemplate);
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void concurrentAcquireHasSingleWinner() throws Exception {
        String key = newKey();
        int threads = 16;
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
        assertThat(winners.get()).as("Redis SETNX 必须只有一个赢家").isEqualTo(1);
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).as("占位必须带 TTL").isPositive();
        store.clear(key);
    }

    @Test
    void releasedPlaceholderReacquiredWithUnifiedPrefix() {
        String key = newKey();
        assertThat(store.tryAcquire(key, Duration.ofSeconds(30))).isTrue();
        assertThat(store.tryAcquire(key, Duration.ofSeconds(30))).isFalse();
        assertThat(key).startsWith(CacheKeys.IDEMPOTENT);
        store.release(key);
        assertThat(store.tryAcquire(key, Duration.ofSeconds(30))).isTrue();
        store.clear(key);
    }

    @Test
    void resultRecordIoWithPendingHolding() {
        String key = newKey();
        IdempotentRecord record = new IdempotentRecord(IdempotentState.SUCCESS, "java.lang.String", "\"首次结果\"",
                System.currentTimeMillis());
        store.saveRecord(key, record, Duration.ofSeconds(30));
        Optional<IdempotentRecord> loaded = store.findRecord(key);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().state()).isEqualTo(IdempotentState.SUCCESS);
        assertThat(loaded.get().className()).isEqualTo("java.lang.String");
        assertThat(loaded.get().payload()).isEqualTo("\"首次结果\"");
        assertThat(loaded.get().hasPayload()).isTrue();

        store.release(key);
        store.tryAcquire(key, Duration.ofSeconds(30));
        assertThat(store.findRecord(key)).as("执行中的占位不应返回结果").isEmpty();
        store.clear(key);
    }

    @Test
    void tokenConsumedAtomicallyOnlyOnce() throws Exception {
        String token = "token-" + UUID.randomUUID();
        store.saveToken(token, "1001", Duration.ofSeconds(30));
        assertThat(redisTemplate.hasKey(IdempotentKeys.tokenKey(token))).isTrue();

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                if (store.consumeToken(token) != null) {
                    winners.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        assertThat(winners.get()).as("Token 只能被消费一次").isEqualTo(1);
        assertThat(store.consumeToken(token)).isNull();
        assertThat(redisTemplate.hasKey(IdempotentKeys.tokenKey(token))).isFalse();
    }

    @Test
    void failureCountAccumulatesWithExpiry() {
        String key = newKey();
        assertThat(store.increaseFailure(key, Duration.ofMinutes(1))).isEqualTo(1L);
        assertThat(store.increaseFailure(key, Duration.ofMinutes(1))).isEqualTo(2L);
        assertThat(store.failureCount(key)).isEqualTo(2L);
        assertThat(redisTemplate.getExpire(IdempotentKeys.failureKey(key), TimeUnit.SECONDS)).isPositive();
        store.clearFailure(key);
        assertThat(store.failureCount(key)).isZero();
    }

    @Test
    void placeholderAcquirableAfterWindowExpiry() throws Exception {
        String key = newKey();
        assertThat(store.tryAcquire(key, Duration.ofMillis(200))).isTrue();
        Thread.sleep(400L);
        assertThat(store.tryAcquire(key, Duration.ofMillis(200))).isTrue();
        store.clear(key);
    }

    @Test
    void resultPayloadKeepsOriginalResultStructure() {
        String key = newKey();
        String payload = JsonUtils.toJson(new cn.slainc.core.result.Result<>(200, "成功", "订单-1", 1700000000000L));
        store.saveRecord(key, new IdempotentRecord(IdempotentState.SUCCESS,
                cn.slainc.core.result.Result.class.getName(), payload, System.currentTimeMillis()),
                Duration.ofSeconds(30));
        IdempotentRecord loaded = store.findRecord(key).orElseThrow();
        assertThat(loaded.payload()).isEqualTo(payload);
        assertThat(loaded.payload()).contains("\"code\":200").contains("\"message\":\"成功\"")
                .contains("\"data\":\"订单-1\"");
        store.clear(key);
    }

    private static String newKey() {
        return CacheKeys.IDEMPOTENT + "test:" + UUID.randomUUID();
    }
}
