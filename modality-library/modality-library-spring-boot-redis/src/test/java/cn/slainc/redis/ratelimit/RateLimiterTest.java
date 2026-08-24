package cn.slainc.redis.ratelimit;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static RateLimiter rateLimiter;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        RedisProperties properties = new RedisProperties();
        properties.getRateLimit().setDefaultLimit(100L);
        properties.getRateLimit().setDefaultWindow(Duration.ofMinutes(1));
        rateLimiter = new RateLimiter(stringRedisTemplate, properties);
    }

    @AfterAll
    static void afterAll() {
        if (stringRedisTemplate != null) {
            rateLimiter.reset("", null);
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void 令牌桶在容量耗尽后拒绝请求() {
        String key = newKey();
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryAcquire(key, RateLimitType.TOKEN_BUCKET, 3L, Duration.ofSeconds(60)))
                    .as("第 %s 次应放行", i + 1)
                    .isTrue();
        }
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.TOKEN_BUCKET, 3L, Duration.ofSeconds(60))).isFalse();
        rateLimiter.reset(key, RateLimitType.TOKEN_BUCKET);
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.TOKEN_BUCKET, 3L, Duration.ofSeconds(60))).isTrue();
    }

    @Test
    void 滑动窗口在时间窗内限制请求数并在窗口外恢复() throws Exception {
        String key = newKey();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.SLIDING_WINDOW, 2L, Duration.ofSeconds(2))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.SLIDING_WINDOW, 2L, Duration.ofSeconds(2))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.SLIDING_WINDOW, 2L, Duration.ofSeconds(2))).isFalse();

        Thread.sleep(2200L);
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.SLIDING_WINDOW, 2L, Duration.ofSeconds(2))).isTrue();
    }

    @Test
    void 漏桶按恒定速率漏水且桶满后拒绝() {
        String key = newKey();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.LEAKY_BUCKET, 2L, Duration.ofSeconds(60))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.LEAKY_BUCKET, 2L, Duration.ofSeconds(60))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.LEAKY_BUCKET, 2L, Duration.ofSeconds(60))).isFalse();
    }

    @Test
    void 固定窗口在计数超过上限后拒绝() {
        String key = newKey();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.FIXED_WINDOW, 2L, Duration.ofSeconds(60))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.FIXED_WINDOW, 2L, Duration.ofSeconds(60))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.FIXED_WINDOW, 2L, Duration.ofSeconds(60))).isFalse();
    }

    @Test
    void 每次申请多个许可() {
        String key = newKey();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.TOKEN_BUCKET, 10L, Duration.ofSeconds(60), 5L)).isTrue();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.TOKEN_BUCKET, 10L, Duration.ofSeconds(60), 5L)).isTrue();
        assertThat(rateLimiter.tryAcquire(key, RateLimitType.TOKEN_BUCKET, 10L, Duration.ofSeconds(60), 1L)).isFalse();
    }

    @Test
    void 统计放行拒绝与拒绝率() {
        rateLimiter.getStats().reset();
        String key = newKey();
        rateLimiter.tryAcquire(key, RateLimitType.FIXED_WINDOW, 1L, Duration.ofSeconds(60));
        rateLimiter.tryAcquire(key, RateLimitType.FIXED_WINDOW, 1L, Duration.ofSeconds(60));

        assertThat(rateLimiter.getStats().getAllowed()).isEqualTo(1L);
        assertThat(rateLimiter.getStats().getRejected()).isEqualTo(1L);
        assertThat(rateLimiter.getStats().getRejectionRate()).isEqualTo(0.5d);
        assertThat(rateLimiter.getStats().toString()).contains("限流统计");
    }

    @Test
    void 键为空时抛出中文异常() {
        assertThatThrownBy(() -> rateLimiter.tryAcquire("  ", RateLimitType.TOKEN_BUCKET, 1L, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("限流键不能为空");
    }

    private String newKey() {
        return "modality:test:ratelimit:" + UUID.randomUUID();
    }
}
