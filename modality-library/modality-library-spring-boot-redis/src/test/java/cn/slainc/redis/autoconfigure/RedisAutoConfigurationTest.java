package cn.slainc.redis.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.cache.CacheTemplate;
import cn.slainc.redis.cache.TwoLevelCache;
import cn.slainc.redis.config.RedisConfigChangeSubscriber;
import cn.slainc.redis.config.RedisDynamicConfigService;
import cn.slainc.redis.key.RedisKeyManager;
import cn.slainc.redis.lock.DistributedLockTemplate;
import cn.slainc.redis.monitor.RedisMonitor;
import cn.slainc.redis.pubsub.RedisPubSubService;
import cn.slainc.redis.queue.RedisDelayQueue;
import cn.slainc.redis.ratelimit.RateLimiter;
import cn.slainc.redis.stream.RedisStreamTemplate;
import cn.slainc.redis.structure.RedisBitMap;
import cn.slainc.redis.structure.RedisScriptExecutor;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

class RedisAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RedisAutoConfiguration.class,
                    RedisConfigAutoConfiguration.class,
                    RedisCacheAutoConfiguration.class,
                    RedisPubSubAutoConfiguration.class,
                    RedisStructureAutoConfiguration.class,
                    RedisRateLimitAutoConfiguration.class,
                    RedisLockAutoConfiguration.class,
                    RedisSaTokenAutoConfiguration.class,
                    RedisJdbcConfigAutoConfiguration.class,
                    RedisMonitorAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> {
                RedisTestSupport.assumeRedis();
                return RedisTestSupport.newConnectionFactory();
            })
            .withBean(DataRedisProperties.class, () -> {
                DataRedisProperties properties = new DataRedisProperties();
                properties.setHost(RedisTestSupport.HOST);
                properties.setPort(RedisTestSupport.PORT);
                properties.setPassword(RedisTestSupport.PASSWORD);
                return properties;
            });

    @Test
    void 默认配置下装配全部能力Bean() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("redisTemplate", RedisTemplate.class).getKeySerializer())
                    .isInstanceOf(StringRedisSerializer.class);
            assertThat(context.getBean(StringRedisTemplate.class)).isNotNull();
            assertThat(context.getBean(RedisMessageListenerContainer.class)).isNotNull();

            assertThat(context.getBean(RedisDynamicConfigService.class)).isNotNull();
            assertThat(context.getBean(DynamicConfigService.class)).isSameAs(context.getBean(RedisDynamicConfigService.class));
            assertThat(context.getBean(RedisConfigChangeSubscriber.class)).isNotNull();
            assertThat(context.getBean(CacheTemplate.class)).isNotNull();
            assertThat(context.getBean(TwoLevelCache.class)).isNotNull();
            assertThat(context.getBean(RateLimiter.class)).isNotNull();
            assertThat(context.getBean(RedissonClient.class)).isNotNull();
            assertThat(context.getBean(DistributedLockTemplate.class)).isNotNull();
            assertThat(context.getBean(RedisPubSubService.class)).isNotNull();
            assertThat(context.getBean(RedisKeyManager.class)).isNotNull();
            assertThat(context.getBean(RedisStreamTemplate.class)).isNotNull();
            assertThat(context.getBean(RedisDelayQueue.class)).isNotNull();
            assertThat(context.getBean(RedisBitMap.class)).isNotNull();
            assertThat(context.getBean(RedisScriptExecutor.class)).isNotNull();
        });
    }

    @Test
    void 缺少compileOnly坐标时相关自动配置安静退避() {
        runner.withClassLoader(new org.springframework.boot.test.context.FilteredClassLoader(
                "cn.dev33.satoken.dao.SaTokenDao",
                "cn.dev33.satoken.dao",
                "org.springframework.jdbc.core.JdbcTemplate",
                "org.springframework.jdbc.core",
                "io.micrometer.core.instrument.MeterRegistry",
                "io.micrometer.core.instrument")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("redisSaTokenDao");
            assertThat(context).doesNotHaveBean("saTokenRedisStore");
            assertThat(context).doesNotHaveBean("jdbcDynamicConfigRepository");
            assertThat(context).doesNotHaveBean(RedisMonitor.class);
        });
    }

    @Test
    void 每项能力都可通过属性单独关闭() {
        runner.withPropertyValues(
                        "modality.redis.cache.enabled=false",
                        "modality.redis.lock.enabled=false",
                        "modality.redis.rate-limit.enabled=false",
                        "modality.redis.pubsub.enabled=false",
                        "modality.redis.config.enabled=false",
                        "modality.redis.key.enabled=false",
                        "modality.redis.stream.enabled=false",
                        "modality.redis.queue.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CacheTemplate.class);
                    assertThat(context).doesNotHaveBean(DistributedLockTemplate.class);
                    assertThat(context).doesNotHaveBean(RateLimiter.class);
                    assertThat(context).doesNotHaveBean(RedisPubSubService.class);
                    assertThat(context).doesNotHaveBean(RedisDynamicConfigService.class);
                    assertThat(context).doesNotHaveBean(RedisKeyManager.class);
                    assertThat(context).doesNotHaveBean(RedisStreamTemplate.class);
                    assertThat(context).doesNotHaveBean(RedisDelayQueue.class);
                    assertThat(context).hasBean("redisTemplate");
                });
    }

    @Test
    void 发布与订阅可分别独立开关() {
        runner.withPropertyValues("modality.redis.config.publish-enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("configBroadcaster");
            assertThat(context).hasBean("redisDynamicConfigService");
            assertThat(context).hasBean("redisConfigChangeSubscriber");
        });
        runner.withPropertyValues("modality.redis.config.subscribe-enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("configBroadcaster");
            assertThat(context).doesNotHaveBean("redisConfigChangeSubscriber");
        });
    }

    @Test
    void 使用方可覆盖默认Bean() {
        runner.withBean("redisTemplate", RedisTemplate.class, () -> {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(RedisTestSupport.newConnectionFactory());
            template.afterPropertiesSet();
            return template;
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("redisTemplate")).isNotNull();
            assertThat(context.getBean("redisTemplate", RedisTemplate.class).getKeySerializer())
                    .isNotInstanceOf(StringRedisSerializer.class);
        });
    }
}
