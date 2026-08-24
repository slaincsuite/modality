package cn.slainc.redis.structure;

import cn.slainc.redis.RedisTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisStructureTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static RedisTemplate<String, Object> redisTemplate;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        redisTemplate = RedisTestSupport.newRedisTemplate(factory);
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void 位图置位读取计数与位运算() {
        RedisBitMap bitMap = new RedisBitMap(redisTemplate);
        String key = "modality:test:bitmap:" + UUID.randomUUID();
        assertThat(bitMap.setBit(key, 7L, true)).isFalse();
        assertThat(bitMap.setBit(key, 7L, false)).isTrue();
        bitMap.setBit(key, 0L, true);
        bitMap.setBit(key, 3L, true);

        assertThat(bitMap.getBit(key, 0L)).isTrue();
        assertThat(bitMap.getBit(key, 1L)).isFalse();
        assertThat(bitMap.bitCount(key)).isEqualTo(2L);
        assertThat(bitMap.bitCount(key, 0L, 0L)).isEqualTo(2L);

        String other = "modality:test:bitmap:" + UUID.randomUUID();
        bitMap.setBit(other, 0L, true);
        assertThat(bitMap.bitOp(RedisStringCommands.BitOperation.OR, key + ":or", key, other)).isPositive();
        assertThat(bitMap.getBit(key + ":or", 0L)).isTrue();

        stringRedisTemplate.unlink(List.of(key, other, key + ":or"));
    }

    @Test
    void 基数统计与合并() {
        RedisHyperLogLog hyperLogLog = new RedisHyperLogLog(redisTemplate);
        String first = "modality:test:hll:" + UUID.randomUUID();
        String second = "modality:test:hll:" + UUID.randomUUID();
        String union = "modality:test:hll:union:" + UUID.randomUUID();

        hyperLogLog.add(first, "甲", "乙", "丙");
        hyperLogLog.add(second, "丙", "丁");
        assertThat(hyperLogLog.size(first)).isEqualTo(3L);
        assertThat(hyperLogLog.size(second)).isEqualTo(2L);
        assertThat(hyperLogLog.union(union, first, second)).isEqualTo(4L);

        hyperLogLog.delete(first);
        assertThat(hyperLogLog.size(first)).isZero();
        stringRedisTemplate.unlink(List.of(second, union));
    }

    @Test
    void 地理坐标写入距离与半径检索() {
        RedisGeo geo = new RedisGeo(redisTemplate);
        String key = "modality:test:geo:" + UUID.randomUUID();
        geo.add(key, 116.397128d, 39.916527d, "北京");
        geo.add(key, 121.473701d, 31.230416d, "上海");
        geo.add(key, 113.264385d, 23.129112d, "广州");

        Double distance = geo.distanceMeters(key, "北京", "上海");
        assertThat(distance).isNotNull().isBetween(1_000_000d, 1_400_000d);

        List<GeoPoint> nearby = geo.radius(key, 116.397128d, 39.916527d, 200_000d, 10);
        assertThat(nearby).extracting(GeoPoint::getMember).contains("北京").doesNotContain("上海");
        assertThat(nearby.get(0).toString()).contains("地理坐标");

        assertThat(geo.hash(key, "北京")).hasSize(1);
        assertThat(geo.remove(key, "广州")).isEqualTo(1L);
        stringRedisTemplate.unlink(List.of(key));
    }

    @Test
    void 管道批量执行返回按顺序的结果() {
        RedisPipeline pipeline = new RedisPipeline(redisTemplate);
        String scope = "modality:test:pipeline:" + UUID.randomUUID() + ":";
        List<Object> results = pipeline.execute(operations -> {
            operations.opsForValue().set(scope + "一", "1");
            operations.opsForValue().set(scope + "二", "2");
            operations.opsForValue().get(scope + "一");
        });
        assertThat(results).hasSize(3);
        assertThat(redisTemplate.opsForValue().get(scope + "二")).isEqualTo("2");
        assertThat(redisTemplate.opsForValue().get(scope + "一")).isEqualTo("1");

        stringRedisTemplate.unlink(List.of(scope + "一", scope + "二"));
    }

    @Test
    void Lua脚本加载缓存与执行() {
        RedisScriptExecutor executor = new RedisScriptExecutor(stringRedisTemplate);
        String scope = "modality:test:script:" + UUID.randomUUID() + ":";
        String script = "redis.call('set', KEYS[1], ARGV[1]) return redis.call('get', KEYS[1])";

        String sha = executor.load(script);
        assertThat(sha).isNotBlank();
        assertThat(executor.cachedSha(script)).isEqualTo(sha);
        assertThat(executor.exists(sha)).isTrue();

        Object result = executor.execute(script, RedisScriptResultType.VALUE, List.of(scope + "键"), "脚本值");
        assertThat(String.valueOf(result)).isEqualTo("脚本值");
        assertThat(stringRedisTemplate.opsForValue().get(scope + "键")).isEqualTo("脚本值");

        Object direct = executor.executeDirect("return 42", RedisScriptResultType.INTEGER, List.of());
        assertThat(((Number) direct).longValue()).isEqualTo(42L);

        String noKeyResult = executor.execute("return ARGV[1]", RedisScriptResultType.VALUE, null, "无键值");
        assertThat(noKeyResult).isEqualTo("无键值");
        executor.clearCache();
        assertThat(executor.cachedSha(script)).isNull();
        executor.flush();
        assertThat(executor.exists(sha)).isFalse();

        stringRedisTemplate.unlink(List.of(scope + "键"));
    }
}
