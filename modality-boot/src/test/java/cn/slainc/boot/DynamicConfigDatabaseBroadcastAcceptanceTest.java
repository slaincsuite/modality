package cn.slainc.boot;

import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.config.ConfigBroadcaster;
import cn.slainc.redis.config.ConfigValueCipher;
import cn.slainc.redis.config.JdbcDynamicConfigRepository;
import cn.slainc.redis.config.RedisConfigBroadcaster;
import cn.slainc.redis.config.RedisConfigChangeSubscriber;
import cn.slainc.redis.config.RedisDynamicConfigService;
import cn.slainc.redis.serialize.RedisSerializers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicConfigDatabaseBroadcastAcceptanceTest {

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/modality"
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";

    private static final String MYSQL_USER = "omni";

    private static final String MYSQL_PASSWORD = "omni-pass";

    private static final String REDIS_HOST = "127.0.0.1";

    private static final int REDIS_PORT = 6379;

    private static final String REDIS_PASSWORD = "omni-redis-pass";

    private static final String SKIP = "本地 Docker 的 MySQL 或 Redis 不可用，已跳过端到端验收";

    private static final long TIMEOUT_MILLIS = 10_000L;

    private static JdbcTemplate jdbcTemplate;

    private static Node publisher;

    private static Node subscriber;

    private static Node staleNode;

    private static CountDownLatch subscriptionReady;

    @BeforeAll
    static void beforeAll() {
        Assumptions.assumeTrue(mysqlAvailable(), SKIP);
        Assumptions.assumeTrue(redisAvailable(), SKIP);
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD));
        jdbcTemplate.execute("SELECT 1");

        publisher = new Node("节点A", true, false);
        subscriber = new Node("节点B", false, true);
        staleNode = new Node("节点C", false, false);
        awaitSubscription();
    }

    @AfterAll
    static void afterAll() {
        close(subscriber);
        close(staleNode);
        close(publisher);
    }

    @Test
    @DisplayName("改数据库 → Redis 广播 → 另一节点刷新本地缓存（真 MySQL + 真 Redis）")
    void databaseChangeIsBroadcastToOtherNode() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String key = "acceptance.db." + suffix;
        String group = "验收分组-" + suffix;

        publisher.service.set(key, "值-1", group);
        assertEquals("值-1", databaseValue(key), "配置应先落到 MySQL");
        System.out.println("[验收] 节点A写入 MySQL，t_dynamic_config 中 config_value = " + databaseValue(key));

        awaitValue(subscriber.service, key, "值-1");
        assertTrue(subscriber.service.isLocallyCached(key), "节点B应已装载本地缓存");
        awaitValue(staleNode.service, key, "值-1");
        System.out.println("[验收] 节点B、节点C 均已读到 值-1 并写入本地缓存");

        publisher.service.set(key, "值-2", group);
        assertEquals("值-2", databaseValue(key), "第二次修改也应落到 MySQL");
        assertEquals("值-1", staleNode.service.localCachedValue(key), "广播到达前节点C本地缓存仍是旧值");
        System.out.println("[验收] 节点A写入 值-2，此时节点C的本地缓存仍是 " + staleNode.service.localCachedValue(key));

        awaitValue(subscriber.service, key, "值-2");
        assertEquals("值-2", subscriber.service.localCachedValue(key));
        System.out.println("[验收] 节点B经 Redis Pub/Sub 广播刷新为 值-2");

        assertEquals("值-1", staleNode.service.getString(key, null),
                "节点C未订阅广播，本地缓存不应被刷新");
        System.out.println("[验收] 负向对照通过：节点C未订阅，仍返回 " + staleNode.service.getString(key, null));

        publisher.service.remove(key);
        awaitRemoved(subscriber.service, key);
        assertEquals(0, countActiveRows(key), "删除后 MySQL 中该配置应被逻辑删除");
        assertTrue(countRows(key) >= 1, "逻辑删除保留行，物理行数应 >= 1");
        System.out.println("[验收] 删除后节点B同步失效；MySQL 有效行 = " + countActiveRows(key)
                + "，物理行 = " + countRows(key) + "（逻辑删除）");

        publisher.service.set(key, "值-3", group);
        awaitValue(subscriber.service, key, "值-3");
        System.out.println("[验收] 逻辑删除后重新写入同一 key 成功，节点B读到 值-3");
        publisher.service.remove(key);
        awaitRemoved(subscriber.service, key);
    }

    @Test
    @DisplayName("数据库持久化在服务实例重建后仍然可读")
    void persistedConfigurationSurvivesRestart() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String key = "acceptance.persist." + suffix;
        publisher.service.set(key, "持久化值", "验收分组-" + suffix);

        Node restarted = new Node("节点D", false, false);
        try {
            assertEquals("持久化值", restarted.service.getString(key, null));
            assertNotNull(databaseValue(key));
        } finally {
            close(restarted);
            publisher.service.remove(key);
        }
    }

    @Test
    @DisplayName("跨节点版本号单调递增")
    void versionIncreasesMonotonically() throws Exception {
        long before = publisher.service.currentVersion();
        publisher.service.set("acceptance.version." + UUID.randomUUID(), "v", "验收分组");
        long after = publisher.service.currentVersion();
        assertTrue(after > before, "版本号应递增，before=" + before + " after=" + after);

        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (subscriber.service.currentVersion() < after && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        assertEquals(after, subscriber.service.currentVersion(), "订阅节点应最终同步到相同版本号");
    }

    private static void awaitSubscription() {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            publisher.service.refreshAll();
            try {
                if (subscriptionReady.await(200L, TimeUnit.MILLISECONDS)) {
                    System.out.println("[验收] 节点B的独立 Redis 连接已建立订阅");
                    return;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待订阅就绪被中断", exception);
            }
        }
        throw new AssertionError("等待节点B建立 Redis 订阅超时");
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
        throw new AssertionError("等待读到 [" + expected + "] 超时，当前 = " + current);
    }

    private static void awaitRemoved(RedisDynamicConfigService service, String key) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (service.getString(key, null) == null) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("等待配置失效超时: " + key);
    }

    private static String databaseValue(String key) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT config_value FROM t_dynamic_config WHERE config_key = ?", String.class, key);
        return values.isEmpty() ? null : values.get(0);
    }

    private static int countRows(String key) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_dynamic_config WHERE config_key = ?", Integer.class, key);
        return count == null ? 0 : count;
    }

    private static int countActiveRows(String key) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_dynamic_config WHERE config_key = ? AND deleted = 0", Integer.class, key);
        return count == null ? 0 : count;
    }

    private static boolean mysqlAvailable() {
        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            return connection.isValid(2);
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean redisAvailable() {
        try {
            LettuceConnectionFactory factory = newConnectionFactory();
            try (var connection = factory.getConnection()) {
                return "PONG".equals(connection.ping());
            } finally {
                factory.destroy();
            }
        } catch (Exception exception) {
            return false;
        }
    }

    private static LettuceConnectionFactory newConnectionFactory() {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(REDIS_HOST, REDIS_PORT);
        server.setPassword(RedisPassword.of(REDIS_PASSWORD));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(5))
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(server, client);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private static void close(Node node) {
        if (node != null) {
            node.close();
        }
    }

    private static final class Node implements AutoCloseable {

        private final LettuceConnectionFactory connectionFactory;

        private final StringRedisTemplate stringRedisTemplate;

        private final RedisMessageListenerContainer container;

        private final RedisDynamicConfigService service;

        private Node(String name, boolean publish, boolean subscribe) {
            this.connectionFactory = newConnectionFactory();
            this.stringRedisTemplate = new StringRedisTemplate(connectionFactory);
            this.stringRedisTemplate.afterPropertiesSet();

            RedisProperties properties = new RedisProperties();
            properties.getConfig().setPublishEnabled(publish);
            properties.getConfig().setSubscribeEnabled(subscribe);

            JdbcDynamicConfigRepository repository = new JdbcDynamicConfigRepository(jdbcTemplate,
                    properties.getConfig().getTable());
            repository.initSchema();

            ConfigBroadcaster broadcaster = publish
                    ? new RedisConfigBroadcaster(stringRedisTemplate, RedisSerializers.plainMapper(),
                    RedisDynamicConfigService.channelOf(properties))
                    : null;
            this.service = new RedisDynamicConfigService(stringRedisTemplate, repository,
                    RedisSerializers.plainMapper(), new PlainCipher(), broadcaster, null, properties);

            this.container = new RedisMessageListenerContainer();
            this.container.setConnectionFactory(connectionFactory);
            if (subscribe) {
                subscriptionReady = new CountDownLatch(1);
                this.container.addMessageListener(
                        new RedisConfigChangeSubscriber(service, RedisSerializers.plainMapper()),
                        new ChannelTopic(service.channel()));
                this.container.addMessageListener((message, pattern) -> subscriptionReady.countDown(),
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
                // 关闭异常不影响验收结论
            }
            connectionFactory.destroy();
        }
    }

    private static final class PlainCipher implements ConfigValueCipher {

        private static final String MARK = "enc:";

        @Override
        public String encrypt(String plainText) {
            return plainText == null ? null
                    : MARK + Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String decrypt(String cipherText) {
            if (cipherText == null) {
                return null;
            }
            return cipherText.startsWith(MARK)
                    ? new String(Base64.getDecoder().decode(cipherText.substring(MARK.length())), StandardCharsets.UTF_8)
                    : cipherText;
        }
    }
}
