package cn.slainc.redis.key;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKeyManagerTest {

    private static final String PREFIX = "modality:test:key:";

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static RedisKeyManager keyManager;

    private String scope;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        RedisTemplate<String, Object> redisTemplate = RedisTestSupport.newRedisTemplate(factory);
        RedisProperties properties = new RedisProperties();
        properties.getKey().setScanCount(10);
        keyManager = new RedisKeyManager(stringRedisTemplate, redisTemplate, properties);
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        scope = PREFIX + UUID.randomUUID().toString().substring(0, 8) + ":";
        stringRedisTemplate.opsForValue().set(scope + "甲", "1");
        stringRedisTemplate.opsForValue().set(scope + "乙", "2");
        stringRedisTemplate.opsForHash().put(scope + "散列", "字段", "值");
    }

    @Test
    void SCAN前缀扫描不遗漏键() {
        List<String> keys = keyManager.scan(scope + "*");
        assertThat(keys).contains(scope + "甲", scope + "乙", scope + "散列");
        assertThat(keyManager.scanSet(scope + "*")).hasSize(3);
        assertThat(keyManager.scan(scope + "*", 2)).hasSize(2);
        assertThat(keyManager.count(scope + "*")).isEqualTo(3L);
    }

    @Test
    void 批量删除按前缀清理() {
        long deleted = keyManager.deleteByPrefix(scope);
        assertThat(deleted).isEqualTo(3L);
        assertThat(keyManager.scan(scope + "*")).isEmpty();
    }

    @Test
    void TTL管理与持久化() {
        assertThat(keyManager.ttl(scope + "甲")).isNull();
        assertThat(keyManager.ttlSeconds(scope + "甲")).isEqualTo(-1L);

        assertThat(keyManager.expire(scope + "甲", Duration.ofSeconds(60))).isTrue();
        assertThat(keyManager.ttl(scope + "甲")).isNotNull();
        assertThat(keyManager.ttlSeconds(scope + "甲")).isBetween(50L, 60L);

        assertThat(keyManager.persist(scope + "甲")).isTrue();
        assertThat(keyManager.ttlSeconds(scope + "甲")).isEqualTo(-1L);
        assertThat(keyManager.expire(scope + "甲", Duration.ZERO)).isFalse();
    }

    @Test
    void 类型判断重命名与内存占用() {
        assertThat(keyManager.dataType(scope + "甲")).isEqualTo(DataType.STRING);
        assertThat(keyManager.type(scope + "散列")).isEqualTo("hash");
        assertThat(keyManager.type(scope + "不存在")).isEqualTo("none");
        assertThat(keyManager.exists(scope + "甲")).isTrue();

        assertThat(keyManager.memoryUsage(scope + "甲")).isNotNull().isPositive();
        assertThat(keyManager.memoryUsage(scope + "不存在")).isNull();

        assertThat(keyManager.rename(scope + "乙", scope + "丙")).isTrue();
        assertThat(keyManager.exists(scope + "乙")).isFalse();
        assertThat(keyManager.exists(scope + "丙")).isTrue();
        assertThat(keyManager.renameIfAbsent(scope + "丙", scope + "甲")).isFalse();
    }

    @Test
    void 单个与批量删除以及触碰与库大小() {
        assertThat(keyManager.touch(scope + "甲", scope + "乙")).isEqualTo(2L);
        assertThat(keyManager.delete(scope + "甲")).isEqualTo(1L);
        assertThat(keyManager.unlink(scope + "乙", scope + "散列")).isEqualTo(2L);
        assertThat(keyManager.dbSize()).isPositive();
    }
}
