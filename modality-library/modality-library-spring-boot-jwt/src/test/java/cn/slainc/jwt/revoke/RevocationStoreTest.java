package cn.slainc.jwt.revoke;

import cn.slainc.jwt.JwtRedisTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RevocationStoreTest {

    private static StringRedisTemplate redis;

    @BeforeAll
    static void beforeAll() {
        JwtRedisTestSupport.requireRedis();
        redis = JwtRedisTestSupport.newStringRedisTemplate(JwtRedisTestSupport.newConnectionFactory());
    }

    @AfterAll
    static void afterAll() {
        if (redis != null) {
            Set<String> keys = redis.keys(RedisRevocationStore.KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
    }

    @Test
    void 内存存储吊销后判定已吊销() {
        MemoryRevocationStore store = new MemoryRevocationStore();
        String jti = UUID.randomUUID().toString();
        assertThat(store.isRevoked(jti)).isFalse();
        store.revoke(jti, Instant.now().plusSeconds(60));
        assertThat(store.isRevoked(jti)).isTrue();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void 内存存储过期记录自动清理() throws Exception {
        MemoryRevocationStore store = new MemoryRevocationStore();
        String jti = UUID.randomUUID().toString();
        store.revoke(jti, Instant.now().plusMillis(300));
        assertThat(store.isRevoked(jti)).isTrue();
        Thread.sleep(500);
        assertThat(store.isRevoked(jti)).as("过期后应视为未吊销").isFalse();
    }

    @Test
    void 内存存储空jti查询返回未吊销() {
        MemoryRevocationStore store = new MemoryRevocationStore();
        assertThat(store.isRevoked(null)).isFalse();
        assertThat(store.isRevoked("")).isFalse();
    }

    @Test
    void 内存存储空jti吊销抛中文异常() {
        MemoryRevocationStore store = new MemoryRevocationStore();
        assertThatThrownBy(() -> store.revoke(null, Instant.now().plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jti");
    }

    @Test
    void Redis存储吊销后判定已吊销() {
        RedisRevocationStore store = new RedisRevocationStore(redis);
        String jti = UUID.randomUUID().toString();
        assertThat(store.isRevoked(jti)).isFalse();
        store.revoke(jti, Instant.now().plusSeconds(60));
        assertThat(store.isRevoked(jti)).isTrue();
        redis.delete(RedisRevocationStore.KEY_PREFIX + jti);
    }

    @Test
    void Redis存储TTL等于距过期的剩余时间() {
        RedisRevocationStore store = new RedisRevocationStore(redis);
        String jti = UUID.randomUUID().toString();
        store.revoke(jti, Instant.now().plusSeconds(30));
        Long ttl = redis.getExpire(RedisRevocationStore.KEY_PREFIX + jti);
        assertThat(ttl).as("TTL 应接近 30 秒").isBetween(25L, 30L);
        redis.delete(RedisRevocationStore.KEY_PREFIX + jti);
    }

    @Test
    void Redis存储键名使用统一前缀() {
        RedisRevocationStore store = new RedisRevocationStore(redis);
        String jti = UUID.randomUUID().toString();
        store.revoke(jti, Instant.now().plusSeconds(60));
        assertThat(redis.hasKey(RedisRevocationStore.KEY_PREFIX + jti)).isTrue();
        assertThat(RedisRevocationStore.KEY_PREFIX).isEqualTo("modality:jwt:revoked:");
        redis.delete(RedisRevocationStore.KEY_PREFIX + jti);
    }

    @Test
    void Redis存储过期后自动失效() throws Exception {
        RedisRevocationStore store = new RedisRevocationStore(redis);
        String jti = UUID.randomUUID().toString();
        store.revoke(jti, Instant.now().plusMillis(300));
        long deadline = System.currentTimeMillis() + 3000;
        while (store.isRevoked(jti) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(store.isRevoked(jti)).as("过期后应自动失效").isFalse();
    }

    @Test
    void Redis存储空jti不产生键() {
        RedisRevocationStore store = new RedisRevocationStore(redis);
        assertThat(store.isRevoked(null)).isFalse();
        assertThatThrownBy(() -> store.revoke("", Instant.now().plusSeconds(60)))
                .hasMessageContaining("jti");
    }

    @Test
    void 服务层按令牌吊销解析jti入库() {
        RevocationService service = new RevocationService(new MemoryRevocationStore());
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String token = header + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"u\",\"jti\":\"svc-jti-" + UUID.randomUUID() + "\",\"exp\":9999999999}")
                        .getBytes()) + ".sig";
        service.revokeByToken(token);
        assertThat(service.isRevoked(jtiOf(token))).isTrue();
    }

    @Test
    void 服务层对缺少jti的令牌给出中文异常() {
        RevocationService service = new RevocationService(new MemoryRevocationStore());
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String token = header + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"u\",\"exp\":9999999999}".getBytes()) + ".sig";
        assertThatThrownBy(() -> service.revokeByToken(token))
                .isInstanceOf(cn.slainc.jwt.verify.JwtVerifyException.class)
                .hasMessageContaining("jti");
    }

    @Test
    void 服务层查询异常时按未吊销放行() {
        RevocationStore broken = new RevocationStore() {
            @Override
            public void revoke(String jti, Instant expiresAt) {
                throw new IllegalStateException("存储不可用");
            }

            @Override
            public boolean isRevoked(String jti) {
                throw new IllegalStateException("存储不可用");
            }
        };
        RevocationService service = new RevocationService(broken);
        assertThat(service.isRevoked("any-jti")).isFalse();
        assertThatThrownBy(() -> service.revoke("any-jti", Instant.now().plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
    }

    private String jtiOf(String token) {
        return cn.slainc.crypto.jwt.JwtCodec.decodeUnverified(token).jwtId();
    }
}
