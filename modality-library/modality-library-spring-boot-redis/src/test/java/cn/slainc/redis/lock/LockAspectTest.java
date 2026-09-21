package cn.slainc.redis.lock;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockAspectTest {

    private static RedissonClient redissonClient;

    private static DistributedLockTemplate template;

    private static SampleService proxy;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + RedisTestSupport.HOST + ":" + RedisTestSupport.PORT)
                .setPassword(RedisTestSupport.PASSWORD)
                .setDatabase(0);
        redissonClient = Redisson.create(config);
        RedisProperties properties = new RedisProperties();
        properties.getLock().setWaitTime(2L);
        properties.getLock().setLeaseTime(10L);
        template = new DistributedLockTemplate(redissonClient, properties);
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(new LockAspect(template, properties));
        proxy = factory.getProxy();
    }

    @AfterAll
    static void afterAll() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void annotatedMethodExecutesUnderLockAndReleases() {
        assertThat(proxy.fixedKey("参数")).isEqualTo("固定键:参数");
        assertThat(template.isLocked("modality:test:aspect:fixed")).isFalse();
    }

    @Test
    void spelKeysResolvedPerMethodWithoutConflict() {
        assertThat(proxy.spelKey(11L)).isEqualTo("动态键:11");
        assertThat(proxy.spelKey(22L)).isEqualTo("动态键:22");
    }

    @Test
    void unannotatedMethodSkippedByAspect() {
        assertThat(proxy.plain()).isEqualTo("无锁");
    }

    static class SampleService {

        @Lock(key = "'modality:test:aspect:fixed'", waitTime = 2L, leaseTime = 10L)
        public String fixedKey(String input) {
            return "固定键:" + input;
        }

        @Lock(key = "'modality:test:aspect:dynamic:' + #id", waitTime = 2L, leaseTime = 10L)
        public String spelKey(Long id) {
            return "动态键:" + id;
        }

        public String plain() {
            return "无锁";
        }
    }
}
