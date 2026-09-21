package cn.slainc.redis.cache;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TwoLevelCacheTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static CacheInvalidationListener listener;

    private static TwoLevelCache cache;

    private static RedisTemplate<String, Object> redisTemplate;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        redisTemplate = RedisTestSupport.newRedisTemplate(factory);
        RedisProperties properties = new RedisProperties();
        properties.getCache().setSecondLevelEnabled(true);
        properties.getCache().setSecondLevelMaxSize(1000L);
        properties.getCache().setSecondLevelTtl(Duration.ofMinutes(5));
        listener = new CacheInvalidationListener();
        cache = new TwoLevelCache(redisTemplate, stringRedisTemplate, properties, listener);
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void twoLevelCachePenetratesThenHitsLocal() {
        String name = "二级-" + UUID.randomUUID().toString().substring(0, 6);
        String key = "键";
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get(name, key, () -> {
            loads.incrementAndGet();
            return "值一";
        });
        String second = cache.get(name, key, () -> {
            loads.incrementAndGet();
            return "值二";
        });

        assertThat(first).isEqualTo("值一");
        assertThat(second).isEqualTo("值一");
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cache.localSize(name)).isEqualTo(1L);
        assertThat(listener.registeredCount()).isPositive();
        assertThat(cache.localStats(name).hitCount()).isEqualTo(1L);
    }

    @Test
    void localCacheReloadsAfterInvalidation() {
        String name = "失效-" + UUID.randomUUID().toString().substring(0, 6);
        String key = "键";
        cache.put(name, key, "旧值", Duration.ofMinutes(1));
        assertThat(cache.get(name, key, () -> "加载值")).isEqualTo("旧值");

        cache.remove(name, key);
        assertThat(cache.get(name, key, () -> "新值")).isEqualTo("新值");
    }

    @Test
    void namespaceClearCleansLocalAndRedis() {
        String name = "清空-" + UUID.randomUUID().toString().substring(0, 6);
        cache.put(name, "甲", "1", Duration.ofMinutes(1));
        cache.put(name, "乙", "2", Duration.ofMinutes(1));
        assertThat(cache.localSize(name)).isEqualTo(2L);

        cache.clear(name);
        assertThat(cache.localSize(name)).isZero();
        assertThat(stringRedisTemplate.hasKey(TwoLevelCache.redisKey(name, "甲"))).isFalse();
    }

    @Test
    void broadcastClearsInProcessLocalCache() {
        String name = "广播-" + UUID.randomUUID().toString().substring(0, 6);
        cache.put(name, "键", "值", Duration.ofMinutes(1));
        assertThat(cache.localSize(name)).isEqualTo(1L);

        CacheInvalidationPublisher publisher = new CacheInvalidationPublisher(stringRedisTemplate);
        publisher.publishInvalidate(name, "键");

        String payload = "I|" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "|"
                + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("键".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        listener.onMessage(new org.springframework.data.redis.connection.DefaultMessage(
                name.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)), null);
        assertThat(cache.localSize(name)).isZero();

        listener.onMessage(new org.springframework.data.redis.connection.DefaultMessage(
                name.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                ("C|" + java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "|")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)), null);
        assertThat(listener.registeredCount()).isPositive();
    }

    @Test
    void keyPrefixConventions() {
        assertThat(TwoLevelCache.redisKey("订单", "1")).isEqualTo("modality:cache:订单:1");
        assertThat(CacheInvalidationListener.channel()).isEqualTo("modality:cache:invalidate");
    }
}
