package cn.slainc.sign;

import cn.slainc.sign.composer.SignComposer;
import cn.slainc.sign.composer.SignResult;
import cn.slainc.sign.config.SignKeys;
import cn.slainc.sign.config.SignProperties;
import cn.slainc.sign.key.SignKeyDefinition;
import cn.slainc.sign.key.SignKeyRegistry;
import cn.slainc.sign.store.RedisSignNonceStore;
import cn.slainc.sign.tool.SignTool;
import cn.slainc.sign.verify.SignVerifyResult;
import cn.slainc.sign.verify.SignVerifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SignRedisStoreTest {

    private static final String HOST = "127.0.0.1";

    private static final int PORT = 6379;

    private static final String PASSWORD = "omni-redis-pass";

    private static final int DATABASE = 11;

    private static final Map<String, List<String>> PARAMS = Map.of("orderNo", List.of("R001"));

    private static LettuceConnectionFactory connectionFactory;

    private static StringRedisTemplate redisTemplate;

    private SignVerifier verifier;

    private SignTool signTool;

    private SignKeyDefinition hmacKey;

    @BeforeAll
    static void requireRedis() {
        if (!probe()) {
            throw new IllegalStateException(
                    "本机 " + HOST + ":" + PORT + " 没有可用的 Redis（密码 " + PASSWORD + "），签名测试必须连接真实 Redis，已快速失败");
        }
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(HOST, PORT);
        server.setPassword(RedisPassword.of(PASSWORD));
        server.setDatabase(DATABASE);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(3))
                .build();
        connectionFactory = new LettuceConnectionFactory(server, client);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        cleanSignKeys();
        hmacKey = SignTestSupport.hmacKey("redis-app", SignAlgorithm.HMAC_SHA256, "redis-secret-001");
        SignProperties properties = SignTestSupport.properties(hmacKey);
        verifier = new SignVerifier(new SignKeyRegistry(properties, new SingleProvider<>(null)),
                new SignComposer(properties), properties, new RedisSignNonceStore(redisTemplate));
        signTool = new SignTool(new SignComposer(properties));
    }

    @AfterEach
    void tearDown() {
        cleanSignKeys();
    }

    private static void cleanSignKeys() {
        Set<String> keys = redisTemplate.keys(SignKeys.PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void nonceAcquireIsAtomicInRedis() {
        RedisSignNonceStore store = new RedisSignNonceStore(redisTemplate);
        assertThat(store.tryAcquire("redis-app", "nonce-redis-001", 300)).isTrue();
        assertThat(store.tryAcquire("redis-app", "nonce-redis-001", 300)).isFalse();
        assertThat(redisTemplate.getExpire(SignKeys.nonceKey("redis-app", "nonce-redis-001"), TimeUnit.SECONDS))
                .isGreaterThan(0);
    }

    @Test
    void nonceReplayIsRejectedInRedisMode() {
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, PARAMS);
        SignVerifyResult first = verifier.verify("redis-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), PARAMS);
        SignVerifyResult second = verifier.verify("redis-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), PARAMS);
        assertThat(first.success()).as(first.reason()).isTrue();
        assertThat(second.success()).isFalse();
        assertThat(second.reason()).contains("重放");
    }

    @Test
    void failureCounterIncrementsWithOneHourTtl() {
        verifier.verify("redis-app", null, 2, "1", "12345678", "bad-sign", PARAMS);
        verifier.verify("redis-app", null, 2, "1", "123456789", "bad-sign", PARAMS);
        assertThat(redisTemplate.opsForValue().get(SignKeys.failKey("redis-app"))).isEqualTo("2");
        Long ttl = redisTemplate.getExpire(SignKeys.failKey("redis-app"), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(3600);
        assertThat(new RedisSignNonceStore(redisTemplate).failureCount("redis-app")).isEqualTo(2);
    }

    private static boolean probe() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 500);
            socket.setSoTimeout(800);
            OutputStream out = socket.getOutputStream();
            out.write(("AUTH " + PASSWORD + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("PING\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[128];
            StringBuilder reply = new StringBuilder();
            long deadline = System.currentTimeMillis() + 1500L;
            while (System.currentTimeMillis() < deadline) {
                int read = in.read(buffer);
                if (read <= 0) {
                    break;
                }
                reply.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                if (reply.toString().contains("PONG")) {
                    return true;
                }
            }
            return reply.toString().contains("PONG");
        } catch (Exception exception) {
            return false;
        }
    }
}
