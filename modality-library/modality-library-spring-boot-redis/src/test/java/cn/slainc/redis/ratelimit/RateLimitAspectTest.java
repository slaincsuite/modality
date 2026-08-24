package cn.slainc.redis.ratelimit;

import cn.slainc.core.context.UserContext;
import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitAspectTest {

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static SampleService proxy;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        RedisProperties properties = new RedisProperties();
        properties.getRateLimit().setDefaultLimit(100L);
        properties.getRateLimit().setDefaultWindow(Duration.ofMinutes(1));
        RateLimiter rateLimiter = new RateLimiter(stringRedisTemplate, properties);
        AspectJProxyFactory aspectFactory = new AspectJProxyFactory(new SampleService());
        aspectFactory.addAspect(new RateLimitAspect(rateLimiter, properties));
        proxy = aspectFactory.getProxy();
    }

    @org.junit.jupiter.api.BeforeEach
    void clearCounterKeys() {
        unlinkByPattern("modality:ratelimit:*SampleService*");
        unlinkByPattern("modality:ratelimit:*modality:test:aspect:*");
    }

    private static void unlinkByPattern(String pattern) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        try (var cursor = stringRedisTemplate.scan(
                org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(500).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (!keys.isEmpty()) {
            stringRedisTemplate.unlink(keys);
        }
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void 接口维度按方法签名限流并在超限时抛出中文异常() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        assertThat(proxy.limited(suffix)).isEqualTo("第1次:" + suffix);
        assertThat(proxy.limited(suffix)).isEqualTo("第2次:" + suffix);
        assertThatThrownBy(() -> proxy.limited(suffix))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("请求过于频繁");
    }

    @Test
    void 自定义SpEL维度按参数分别限流() {
        assertThat(proxy.custom("甲")).isEqualTo("自定义:甲");
        assertThat(proxy.custom("甲")).isEqualTo("自定义:甲");
        assertThatThrownBy(() -> proxy.custom("甲")).isInstanceOf(RateLimitExceededException.class);
        assertThat(proxy.custom("乙")).isEqualTo("自定义:乙");
    }

    @Test
    void 用户维度在无登录上下文时按匿名用户限流() {
        UserContext.clear();
        assertThat(proxy.byUser()).isEqualTo("用户维度");
        assertThat(proxy.byUser()).isEqualTo("用户维度");
        assertThatThrownBy(() -> proxy.byUser()).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void 未标注注解的方法不做限流() {
        for (int i = 0; i < 5; i++) {
            assertThat(proxy.plain()).isEqualTo("无限流");
        }
    }

    static class SampleService {

        private static final java.util.concurrent.atomic.AtomicInteger COUNTER =
                new java.util.concurrent.atomic.AtomicInteger();

        @RateLimit(dimension = RateLimitDimension.API, limit = 2L, windowSeconds = 60L,
                type = RateLimitType.FIXED_WINDOW)
        public String limited(String suffix) {
            return "第" + COUNTER.incrementAndGet() + "次:" + suffix;
        }

        @RateLimit(dimension = RateLimitDimension.CUSTOM, key = "'modality:test:aspect:custom:' + #name",
                limit = 2L, windowSeconds = 60L, type = RateLimitType.FIXED_WINDOW)
        public String custom(String name) {
            return "自定义:" + name;
        }

        @RateLimit(dimension = RateLimitDimension.USER, limit = 2L, windowSeconds = 60L,
                type = RateLimitType.FIXED_WINDOW)
        public String byUser() {
            return "用户维度";
        }

        public String plain() {
            return "无限流";
        }
    }
}
