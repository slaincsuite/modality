package cn.slainc.redis.dao;

import cn.slainc.redis.RedisTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SaTokenRedisStoreTest {

    private static final String PREFIX = "modality:test:dao:";

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static RedisTemplate<String, Object> redisTemplate;

    private static SaTokenRedisStore store;

    private String key;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        redisTemplate = RedisTestSupport.newRedisTemplate(factory);
        store = new SaTokenRedisStore(stringRedisTemplate, redisTemplate,
                cn.slainc.redis.serialize.RedisSerializers.jsonMapper(), 10);
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
        key = PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void stringWriteReadWithTtl() {
        store.set(key, "令牌内容", 100L);
        assertThat(store.get(key)).isEqualTo("令牌内容");
        assertThat(store.getTimeout(key)).isBetween(90L, 100L);
    }

    @Test
    void minusOneMeansNeverExpire() {
        store.set(key, "长期令牌", SaTokenRedisStore.NEVER_EXPIRE);
        assertThat(store.get(key)).isEqualTo("长期令牌");
        assertThat(store.getTimeout(key)).isEqualTo(SaTokenRedisStore.NEVER_EXPIRE);
    }

    @Test
    void minusTwoMeansNoWrite() {
        store.set(key, "不应写入", SaTokenRedisStore.NOT_VALUE_EXPIRE);
        assertThat(store.get(key)).isNull();
        assertThat(store.getTimeout(key)).isEqualTo(SaTokenRedisStore.NOT_VALUE_EXPIRE);
    }

    @Test
    void updateKeepsOriginalExpiry() {
        store.set(key, "旧值", 120L);
        long before = store.getTimeout(key);
        store.update(key, "新值");

        assertThat(store.get(key)).isEqualTo("新值");
        assertThat(store.getTimeout(key)).isBetween(before - 5L, before);
        assertThat(store.getTimeout(key)).isGreaterThan(0L);
    }

    @Test
    void updateSkipsMissingKey() {
        store.update(key, "新值");
        assertThat(store.get(key)).isNull();
        assertThat(store.getTimeout(key)).isEqualTo(SaTokenRedisStore.NOT_VALUE_EXPIRE);
    }

    @Test
    void updateTimeoutOnlyChangesExpiry() {
        store.set(key, "保持不变", 300L);
        store.updateTimeout(key, 600L);

        assertThat(store.get(key)).isEqualTo("保持不变");
        assertThat(store.getTimeout(key)).isBetween(590L, 600L);
    }

    @Test
    void updateTimeoutMinusOnePersistsWithoutChange() {
        store.set(key, "转永久", 300L);
        store.updateTimeout(key, SaTokenRedisStore.NEVER_EXPIRE);

        assertThat(store.get(key)).isEqualTo("转永久");
        assertThat(store.getTimeout(key)).isEqualTo(SaTokenRedisStore.NEVER_EXPIRE);
    }

    @Test
    void updateTimeoutZeroOrMinusTwoNoop() {
        store.set(key, "原值", 300L);
        store.updateTimeout(key, 0L);
        store.updateTimeout(key, SaTokenRedisStore.NOT_VALUE_EXPIRE);
        assertThat(store.getTimeout(key)).isBetween(290L, 300L);
    }

    @Test
    void deletingMissingKeyIsSilent() {
        store.delete(key);
        store.delete(key);
        assertThat(store.get(key)).isNull();
    }

    @Test
    void objectReadWriteExpiryAndDelete() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("会话", "会话-1");
        payload.put("计数", 7);
        store.setObject(key, payload, 200L);

        assertThat(store.getObject(key)).isInstanceOf(Map.class);
        assertThat(store.getObject(key, Map.class)).containsEntry("会话", "会话-1");
        assertThat(store.getObjectTimeout(key)).isBetween(190L, 200L);
    }

    @Test
    void objectUpdateKeepsExpiry() {
        store.setObject(key, "对象旧值", 400L);
        long before = store.getObjectTimeout(key);
        store.updateObject(key, "对象新值");

        assertThat(store.getObject(key)).isEqualTo("对象新值");
        assertThat(store.getObjectTimeout(key)).isBetween(before - 5L, before);

        store.updateObjectTimeout(key, 900L);
        assertThat(store.getObject(key)).isEqualTo("对象新值");
        assertThat(store.getObjectTimeout(key)).isBetween(890L, 900L);

        store.updateObjectTimeout(key, SaTokenRedisStore.NEVER_EXPIRE);
        assertThat(store.getObject(key)).isEqualTo("对象新值");
        assertThat(store.getObjectTimeout(key)).isEqualTo(SaTokenRedisStore.NEVER_EXPIRE);

        store.deleteObject(key);
        assertThat(store.getObject(key)).isNull();
    }

    @Test
    void objectConvertsToTargetType() {
        SaTokenRedisStoreTest.Payload payload = new SaTokenRedisStoreTest.Payload();
        payload.setName("会话数据");
        payload.setCount(3);
        store.setObject(key, payload, 60L);

        Payload restored = store.getObject(key, Payload.class);
        assertThat(restored.getName()).isEqualTo("会话数据");
        assertThat(restored.getCount()).isEqualTo(3);
    }

    @Test
    void searchDataFiltersSortsAndPages() {
        String scope = PREFIX + "search-" + UUID.randomUUID().toString().substring(0, 6) + ":";
        stringRedisTemplate.opsForValue().set(scope + "c", "1");
        stringRedisTemplate.opsForValue().set(scope + "a", "1");
        stringRedisTemplate.opsForValue().set(scope + "b", "1");
        stringRedisTemplate.opsForValue().set(scope + "other", "1");

        List<String> all = store.searchData(scope, "", 0, 10, true);
        assertThat(all).containsExactly(scope + "a", scope + "b", scope + "c", scope + "other");

        List<String> page = store.searchData(scope, "", 1, 2, true);
        assertThat(page).containsExactly(scope + "b", scope + "c");

        List<String> firstPage = store.searchData(scope, "", 0, 2, false);
        assertThat(firstPage).hasSize(2);

        List<String> keyword = store.searchData(scope, "other", 0, 10, true);
        assertThat(keyword).containsExactly(scope + "other");

        assertThat(store.searchData(scope, "", 99, 10, true)).isEmpty();
        assertThat(store.searchData(scope, "", 0, 0, true)).isEmpty();

        stringRedisTemplate.unlink(List.of(scope + "a", scope + "b", scope + "c", scope + "other"));
    }

    static class Payload {

        private String name;

        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
