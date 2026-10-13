package cn.slainc.idempotent;

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

public final class IdempotentRedisTestSupport {

    public static final String HOST = "127.0.0.1";

    public static final int PORT = 6379;

    public static final String PASSWORD = System.getenv().getOrDefault("MODALITY_REDIS_PASSWORD", "");

    private static volatile Boolean available;

    private IdempotentRedisTestSupport() {
    }

    public static void requireRedis() {
        if (isRedisAvailable()) {
            return;
        }
        throw new IllegalStateException(unavailableReason(HOST, PORT, PASSWORD));
    }

    public static String unavailableReason(String host, int port, String password) {
        return "本机 " + host + ":" + port + " 没有可用的 Redis（密码 " + password
                + "），幂等验收测试必须连接真实 Redis，已快速失败";
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

    public static LettuceConnectionFactory newConnectionFactory() {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(HOST, PORT);
        server.setPassword(RedisPassword.of(PASSWORD));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(3))
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(server, client);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    public static StringRedisTemplate newStringRedisTemplate(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }

    public static boolean probe(String host, int port, String password) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            socket.setSoTimeout(800);
            OutputStream out = socket.getOutputStream();
            out.write(("AUTH " + password + "\r\n").getBytes(StandardCharsets.UTF_8));
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

    private static boolean probe() {
        return probe(HOST, PORT, PASSWORD);
    }
}
