package cn.slainc.jwt;

import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;

public final class JwtRedisTestSupport {

    public static final String HOST = "127.0.0.1";

    public static final int PORT = 6379;

    public static final String PASSWORD = System.getenv().getOrDefault("MODALITY_REDIS_PASSWORD", "");

    public static final int DATABASE = 4;

    private JwtRedisTestSupport() {
    }

    public static void requireRedis() {
        LettuceConnectionFactory factory = null;
        try {
            factory = newConnectionFactory();
            StringRedisTemplate template = newStringRedisTemplate(factory);
            template.opsForValue().set("modality:jwt:probe:" + System.nanoTime(), "1", Duration.ofSeconds(5));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("本机 " + HOST + ":" + PORT + " 没有可用的 Redis（密码 " + PASSWORD
                    + "），jwt 验收测试必须连接真实 Redis，已快速失败", exception);
        } finally {
            if (factory != null) {
                factory.destroy();
            }
        }
    }

    public static LettuceConnectionFactory newConnectionFactory() {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(HOST, PORT);
        server.setPassword(RedisPassword.of(PASSWORD));
        server.setDatabase(DATABASE);
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

    public static void cleanJwtKeys(StringRedisTemplate template) {
        Set<String> keys = template.keys("modality:jwt:*");
        if (keys != null && !keys.isEmpty()) {
            template.delete(keys);
        }
    }
}
