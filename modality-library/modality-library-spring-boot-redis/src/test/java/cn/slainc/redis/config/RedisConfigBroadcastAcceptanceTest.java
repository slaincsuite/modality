package cn.slainc.redis.config;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.serialize.RedisSerializers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisConfigBroadcastAcceptanceTest {

    private static final String KEY = "acceptance.broadcast.key";

    private static final String GROUP = "验收分组";

    private static final long TIMEOUT_MILLIS = 10000L;

    private static Node publisher;

    private static Node subscriber;

    private static Node isolated;

    private static LettuceConnectionFactory adminFactory;

    private static StringRedisTemplate admin;

    private static final CountDownLatch SUBSCRIBED = new CountDownLatch(1);

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        adminFactory = RedisTestSupport.newConnectionFactory();
        admin = RedisTestSupport.newStringRedisTemplate(adminFactory);
        admin.delete(List.of("modality:config:store", "modality:config:version"));

        publisher = new Node("节点A-仅发布", true, false, null);
        subscriber = new Node("节点B-仅订阅", false, true, null);
        isolated = new Node("节点C-不订阅", false, false, null);

        awaitSubscription();
    }

    @AfterAll
    static void afterAll() {
        if (admin != null) {
            admin.delete(List.of("modality:config:store", "modality:config:version"));
        }
        close(publisher);
        close(subscriber);
        close(isolated);
        if (adminFactory != null) {
            adminFactory.destroy();
        }
    }

    @Test
    @Order(1)
    void storeAndBroadcastNamingConventions() {
        assertThat(publisher.service.storeKey()).isEqualTo("modality:config:store");
        assertThat(publisher.service.channel()).isEqualTo("modality:config:broadcast");
        assertThat(publisher.service.nodeId()).isNotEqualTo(subscriber.service.nodeId());
        System.out.println("[验收] 存储键=" + publisher.service.storeKey() + "，广播频道=" + publisher.service.channel()
                + "，节点A标识=" + publisher.service.nodeId() + "，节点B标识=" + subscriber.service.nodeId());
    }

    @Test
    @Order(2)
    void nodeAChangeInvalidatesNodeBCache() throws Exception {
        String version = UUID.randomUUID().toString().substring(0, 6);
        String key = KEY + "." + version;
        String group = GROUP + version;

        assertThat(subscriber.service.getString(key, null)).isNull();
        assertThat(subscriber.service.isLocallyCached(key)).isFalse();

        publisher.service.set(key, "值-1", group);
        System.out.println("[验收] 节点A写入 值-1，当前版本号=" + publisher.service.currentVersion());

        awaitValue(subscriber.service, key, "值-1");
        assertThat(subscriber.service.isLocallyCached(key)).isTrue();
        assertThat(subscriber.service.localCachedValue(key)).isEqualTo("值-1");
        System.out.println("[验收] 节点B首次读取到 值-1，本地缓存已装载，本地版本=" + subscriber.service.currentVersion());

        assertThat(isolated.service.getString(key, null)).isEqualTo("值-1");
        assertThat(isolated.service.isLocallyCached(key)).isTrue();
        System.out.println("[验收] 节点C（不订阅）读取到 值-1 并写入本地缓存，用于负向对照");

        assertThat(storeValue(admin, key)).contains("值-1");
        assertThat(admin.opsForValue().get("modality:config:version")).isNotNull();

        publisher.service.set(key, "值-2", group);
        long publishedVersion = publisher.service.currentVersion();
        System.out.println("[验收] 节点A改写入 值-2，广播版本号=" + publishedVersion);

        assertThat(storeValue(admin, key)).contains("值-2");
        assertThat(subscriber.service.localCachedValue(key)).isEqualTo("值-1");

        awaitValue(subscriber.service, key, "值-2");
        assertThat(subscriber.service.localCachedValue(key)).isEqualTo("值-2");
        System.out.println("[验收] 节点B经 Pub/Sub 广播失效本地缓存并读到 值-2，新版本="
                + subscriber.service.currentVersion());

        assertThat(isolated.service.getString(key, null)).isEqualTo("值-1");
        System.out.println("[验收] 负向对照：节点C未订阅，本地缓存仍为 值-1，证明节点B的新值确实来自广播而非缓存过期");

        publisher.service.remove(key);
        awaitRemoved(subscriber.service, key);
        System.out.println("[验收] 节点A删除配置后节点B同步失效，读取结果=" + subscriber.service.getString(key, null));
    }

    private static void awaitValue(RedisDynamicConfigService service, String key, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        String current = null;
        while (System.currentTimeMillis() < deadline) {
            current = service.getString(key, null);
            if (expected.equals(current)) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("等待节点读取到 [" + expected + "] 超时，当前值=" + current);
    }

    private static void awaitRemoved(RedisDynamicConfigService service, String key) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        Object current = "占位";
        while (System.currentTimeMillis() < deadline) {
            current = service.getString(key, null);
            if (current == null) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("等待节点失效 [" + key + "] 超时，当前值=" + current);
    }

    private static String storeValue(StringRedisTemplate template, String key) {
        Object value = template.opsForHash().get("modality:config:store", key);
        return value == null ? null : String.valueOf(value);
    }

    private static void awaitSubscription() {
        long start = System.currentTimeMillis();
        long deadline = start + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            publisher.service.refreshAll();
            try {
                if (SUBSCRIBED.await(100L, TimeUnit.MILLISECONDS)) {
                    System.out.println("[验收] 节点B的独立 Redis 连接已在 " + (System.currentTimeMillis() - start)
                            + " 毫秒内收到广播消息，订阅链路就绪");
                    return;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待节点B建立订阅被中断", exception);
            }
        }
        throw new AssertionError("等待节点B建立 Redis 订阅超时");
    }

    private static void close(Node node) {
        if (node != null) {
            node.close();
        }
    }

    private static final class Node implements AutoCloseable {

        private final String name;

        private final LettuceConnectionFactory connectionFactory;

        private final StringRedisTemplate stringRedisTemplate;

        private final RedisTemplate<String, Object> redisTemplate;

        private final RedisMessageListenerContainer container;

        private final RedisDynamicConfigService service;

        private Node(String name, boolean publish, boolean subscribe, String unused) {
            this.name = name;
            this.connectionFactory = RedisTestSupport.newConnectionFactory();
            this.stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(connectionFactory);
            this.redisTemplate = RedisTestSupport.newRedisTemplate(connectionFactory);

            RedisProperties properties = new RedisProperties();
            properties.getConfig().setPublishEnabled(publish);
            properties.getConfig().setSubscribeEnabled(subscribe);

            ConfigBroadcaster broadcaster = publish
                    ? new RedisConfigBroadcaster(stringRedisTemplate, RedisSerializers.plainMapper(),
                    RedisDynamicConfigService.channelOf(properties))
                    : null;
            this.service = new RedisDynamicConfigService(stringRedisTemplate,
                    new InMemoryDynamicConfigRepository(),
                    RedisSerializers.plainMapper(),
                    new TestCipher(),
                    broadcaster,
                    null,
                    properties);

            this.container = new RedisMessageListenerContainer();
            this.container.setConnectionFactory(connectionFactory);
            if (subscribe) {
                this.container.addMessageListener(
                        new RedisConfigChangeSubscriber(service, RedisSerializers.plainMapper()),
                        new ChannelTopic(service.channel()));
                this.container.addMessageListener((message, pattern) -> SUBSCRIBED.countDown(),
                        new ChannelTopic(service.channel()));
            }
            this.container.afterPropertiesSet();
            this.container.start();
        }

        @Override
        public void close() {
            try {
                container.destroy();
            } catch (Exception ignored) {
                // 关闭阶段的异常不影响验收结论
            }
            connectionFactory.destroy();
        }
    }

    private static final class TestCipher implements ConfigValueCipher {

        @Override
        public String encrypt(String plainText) {
            return "加密:" + Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String decrypt(String cipherText) {
            return new String(Base64.getDecoder().decode(cipherText.substring("加密:".length())),
                    StandardCharsets.UTF_8);
        }
    }
}
