package cn.slainc.redis;

import cn.slainc.redis.serialize.RedisSerializers;
import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class RedisTestSupport {

    public static final String HOST = "127.0.0.1";

    public static final int PORT = 6379;

    public static final String PASSWORD = "omni-redis-pass";

    public static final String SKIP_REASON = "本地 127.0.0.1:6379 没有可用的 Redis，已跳过集成测试";

    private static volatile Boolean available;

    private RedisTestSupport() {
    }

    public static boolean isRedisAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        boolean result = probe();
        available = result;
        return result;
    }

    public static void assumeRedis() {
        Assumptions.assumeTrue(isRedisAvailable(), SKIP_REASON);
    }

    public static void assumeClassPresent(String className, String reason) {
        boolean present;
        try {
            Class.forName(className, false, RedisTestSupport.class.getClassLoader());
            present = true;
        } catch (Throwable ignored) {
            present = false;
        }
        Assumptions.assumeTrue(present, reason);
    }

    public static LettuceConnectionFactory newConnectionFactory() {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(HOST, PORT);
        server.setPassword(RedisPassword.of(PASSWORD));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(5))
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(server, client);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    public static StringRedisTemplate newStringRedisTemplate() {
        return newStringRedisTemplate(newConnectionFactory());
    }

    public static StringRedisTemplate newStringRedisTemplate(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }

    public static RedisTemplate<String, Object> newRedisTemplate(LettuceConnectionFactory factory) {
        RedisSerializer<Object> valueSerializer = RedisSerializers.compressedJson(64);
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializers.string());
        template.setHashKeySerializer(RedisSerializers.string());
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.setEnableDefaultSerializer(false);
        template.afterPropertiesSet();
        return template;
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
