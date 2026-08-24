package cn.slainc.redis.cache;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.serialize.RedisSerializers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheFactoryTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static CacheManager cacheManager;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        RedisProperties properties = new RedisProperties();
        properties.getCache().setDefaultTtl(Duration.ofMinutes(2));
        cacheManager = RedisCacheFactory.create(factory, properties, RedisSerializers.json());
        clearAcceptanceKeys();
    }

    @AfterAll
    static void afterAll() {
        clearAcceptanceKeys();
        if (factory != null) {
            factory.destroy();
        }
    }

    private static void clearAcceptanceKeys() {
        if (stringRedisTemplate == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        try (var cursor = stringRedisTemplate.scan(
                org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match("modality:cache:验收*").count(500).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (!keys.isEmpty()) {
            stringRedisTemplate.unlink(keys);
        }
    }

    @Test
    void 缓存管理器写入读取与键前缀符合约定() throws Exception {
        Cache cache = cacheManager.getCache("验收缓存");
        assertThat(cache).isNotNull();
        cache.put("键一", "值一");

        awaitKey("modality:cache:验收缓存:键一");
        awaitValue(cache, "键一", "值一");

        cache.evict("键一");
        awaitNull(cache, "键一");
    }

    @Test
    void 默认配置带过期时间且写入的实际TTL受控() throws Exception {
        Cache cache = cacheManager.getCache("验收过期");
        cache.put("键二", "值二");
        awaitValue(cache, "键二", "值二");
        awaitKey("modality:cache:验收过期:键二");
        Long ttl = stringRedisTemplate.getExpire("modality:cache:验收过期:键二",
                java.util.concurrent.TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isBetween(1L, 120L);
    }

    @Test
    void 覆写已存在键后读取到最新值() throws Exception {
        Cache cache = cacheManager.getCache("验收覆写");
        cache.put("键三", "旧值");
        awaitValue(cache, "键三", "旧值");
        cache.put("键三", "新值");
        awaitValue(cache, "键三", "新值");
        cache.evict("键三");
        awaitNull(cache, "键三");
    }

    // Spring Data Redis 4 新建 RedisCache 后首次读写存在短暂可见性滞后，按最终一致断言
    private static void awaitKey(String key) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("等待 Redis 键 [" + key + "] 可见超时");
    }

    private static void awaitValue(Cache cache, String key, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        Object current = null;
        while (System.currentTimeMillis() < deadline) {
            current = cache.get(key, String.class);
            if (expected.equals(current)) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("等待缓存读取到 [" + expected + "] 超时，当前值=" + current);
    }

    private static void awaitNull(Cache cache, String key) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (cache.get(key) == null) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("等待缓存键 [" + key + "] 失效超时");
    }

    @Test
    void 默认配置对象可独立构造且序列化器为空时使用内置实现() {
        RedisProperties properties = new RedisProperties();
        org.springframework.data.redis.cache.RedisCacheConfiguration configuration =
                RedisCacheFactory.defaultConfiguration(properties, null);
        assertThat(configuration).isNotNull();
        assertThat(properties.getCache().getDefaultTtl()).isEqualTo(Duration.ofMinutes(30));
    }
}
