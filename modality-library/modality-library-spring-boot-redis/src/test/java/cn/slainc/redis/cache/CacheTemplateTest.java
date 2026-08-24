package cn.slainc.redis.cache;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CacheTemplateTest {

    private static final String PREFIX = "modality:test:cache:";

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static CacheTemplate cacheTemplate;

    private String key;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        RedisTemplate<String, Object> redisTemplate = RedisTestSupport.newRedisTemplate(factory);
        RedisProperties properties = new RedisProperties();
        properties.getCache().setDefaultTtl(Duration.ofMinutes(5));
        properties.getCache().setNullValueTtl(Duration.ofSeconds(30));
        properties.getCache().setLockWaitMillis(3000L);
        properties.getCache().setSpinIntervalMillis(20L);
        cacheTemplate = new CacheTemplate(redisTemplate, stringRedisTemplate, properties);
    }

    @AfterAll
    static void afterAll() {
        if (stringRedisTemplate != null) {
            List<String> keys = new ArrayList<>();
            try (var cursor = stringRedisTemplate.scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions().match(PREFIX + "*").count(500).build())) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (!keys.isEmpty()) {
                stringRedisTemplate.unlink(keys);
            }
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        key = PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void 读穿加载并写入Redis() {
        AtomicInteger loads = new AtomicInteger();
        String first = cacheTemplate.get(key, () -> {
            loads.incrementAndGet();
            return "首次加载";
        });
        String second = cacheTemplate.get(key, () -> {
            loads.incrementAndGet();
            return "再次加载";
        });

        assertThat(first).isEqualTo("首次加载");
        assertThat(second).isEqualTo("首次加载");
        assertThat(loads.get()).isEqualTo(1);
        assertThat(stringRedisTemplate.hasKey(key)).isTrue();
    }

    @Test
    void 空值缓存拦截穿透且第二次不再调用loader() {
        AtomicInteger loads = new AtomicInteger();
        String first = cacheTemplate.get(key, () -> {
            loads.incrementAndGet();
            return null;
        });
        String second = cacheTemplate.get(key, () -> {
            loads.incrementAndGet();
            return "不应出现";
        });
        assertThat(first).isNull();
        assertThat(second).isNull();
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cacheTemplate.stats().getNullHits()).isPositive();
    }

    @Test
    void 写入读取删除与存在判断() {
        cacheTemplate.put(key, "缓存值");
        assertThat(cacheTemplate.get(key, () -> "兜底")).isEqualTo("缓存值");
        assertThat(cacheTemplate.exists(key)).isTrue();

        assertThat(cacheTemplate.remove(key)).isTrue();
        assertThat(cacheTemplate.exists(key)).isFalse();
    }

    @Test
    void 过期时间带有随机抖动且不小于下限() {
        cacheTemplate.put(key, "抖动值", Duration.ofSeconds(100));
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isBetween(1L, 110L);
    }

    @Test
    void 单飞保证并发场景下loader只执行一次() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        List<String> results = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        results.add(cacheTemplate.get(key, () -> {
                            loads.incrementAndGet();
                            try {
                                Thread.sleep(120L);
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                            }
                            return "单飞结果";
                        }));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).hasSize(threads).containsOnly("单飞结果");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void 按键前缀批量清理并且不使用KEYS命令() {
        String scope = PREFIX + "clear-" + UUID.randomUUID().toString().substring(0, 6) + ":";
        cacheTemplate.put(scope + "一", "1");
        cacheTemplate.put(scope + "二", "2");
        cacheTemplate.put(PREFIX + "其他-" + UUID.randomUUID().toString().substring(0, 4), "3");

        long removed = cacheTemplate.clear(scope);
        assertThat(removed).isEqualTo(2L);
        assertThat(stringRedisTemplate.hasKey(scope + "一")).isFalse();
        assertThat(stringRedisTemplate.hasKey(scope + "二")).isFalse();
    }

    @Test
    void 统计快照字段与命中率() {
        cacheTemplate.resetStats();
        cacheTemplate.put(key, "统计值");
        cacheTemplate.get(key, () -> "兜底");
        cacheTemplate.get(PREFIX + "不存在", () -> "兜底");
        CacheStatsSnapshot snapshot = cacheTemplate.stats();
        assertThat(snapshot.getHits()).isEqualTo(1L);
        assertThat(snapshot.getMisses()).isEqualTo(1L);
        assertThat(snapshot.getHitRate()).isEqualTo(0.5d);
        assertThat(snapshot.toString()).contains("缓存统计");
    }

    @Test
    void 键为空时抛出中文异常() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cacheTemplate.get(null, () -> "值"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缓存键不能为空");
    }
}
