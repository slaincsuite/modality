package cn.slainc.jwt.jwks;

import cn.slainc.crypto.jwt.Jwk;
import cn.slainc.crypto.jwt.JwkSet;
import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.jwt.JwtRedisTestSupport;
import cn.slainc.jwt.MapDynamicConfigService;
import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.key.JwtKeyManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JwksServiceTest {

    private static StringRedisTemplate redis;

    @BeforeAll
    static void beforeAll() {
        JwtRedisTestSupport.requireRedis();
        redis = JwtRedisTestSupport.newStringRedisTemplate(JwtRedisTestSupport.newConnectionFactory());
    }

    @BeforeEach
    void clearCache() {
        redis.delete(JwksService.CACHE_KEY);
    }

    @AfterAll
    static void afterAll() {
        if (redis != null) {
            redis.delete(JwksService.CACHE_KEY);
        }
    }

    private JwtKeyManager manager(JwtAlgorithm... algorithms) {
        JwtProperties properties = new JwtProperties();
        int index = 0;
        for (JwtAlgorithm algorithm : algorithms) {
            JwtProperties.KeySpec spec = new JwtProperties.KeySpec();
            spec.setKid("jwks-" + algorithm.alg() + "-" + (++index));
            spec.setAlgorithm(algorithm);
            properties.getKeys().add(spec);
        }
        return new JwtKeyManager(properties);
    }

    private JwksService service(JwtKeyManager keyManager) {
        return new JwksService(keyManager, new JwtSettings(new JwtProperties(), new MapDynamicConfigService()), redis);
    }

    @Test
    void jwksContainsRsaKtyNAndE() {
        JwksService service = service(manager(JwtAlgorithm.RS256));
        JwkSet jwkSet = JwkSet.parse(service.getJwksJson());
        assertThat(jwkSet.size()).isEqualTo(1);
        Jwk jwk = jwkSet.first();
        assertThat(jwk.kty()).isEqualTo("RSA");
        assertThat(jwk.kid()).startsWith("jwks-RS256-");
        assertThat(jwk.use()).isEqualTo("sig");
        assertThat(jwk.alg()).isEqualTo("RS256");
        assertThat(jwk.toMap()).containsKeys("n", "e");
    }

    @Test
    void jwksContainsEcCrvXAndY() {
        JwksService service = service(manager(JwtAlgorithm.ES256));
        Jwk jwk = JwkSet.parse(service.getJwksJson()).first();
        assertThat(jwk.kty()).isEqualTo("EC");
        assertThat(jwk.crv()).isEqualTo("P-256");
        assertThat(jwk.toMap()).containsKeys("x", "y");
    }

    @Test
    void symmetricKeysNeverAppearInJwks() {
        JwksService hsOnly = service(manager(JwtAlgorithm.HS256));
        assertThat(JwkSet.parse(hsOnly.getJwksJson()).isEmpty())
                .as("仅 HS 密钥时 JWKS 应为空").isTrue();

        redis.delete(JwksService.CACHE_KEY);
        JwkSet mixed = JwkSet.parse(service(manager(JwtAlgorithm.HS256, JwtAlgorithm.RS256)).getJwksJson());
        assertThat(mixed.size()).isEqualTo(1);
        assertThat(mixed.first().kty()).isNotEqualTo("oct");
    }

    @Test
    void jwksParseableByJwkSetWithMatchingKid() {
        JwtKeyManager keyManager = manager(JwtAlgorithm.RS256, JwtAlgorithm.ES256);
        JwkSet parsed = JwkSet.parse(service(keyManager).getJwksJson());
        List<String> kids = parsed.keys().stream().map(Jwk::kid).toList();
        assertThat(kids).containsExactlyInAnyOrder(
                keyManager.allEntries().get(0).kid(), keyManager.allEntries().get(1).kid());
    }

    @Test
    void redisCacheAvoidsRecomputeOnSameInstance() {
        JwtKeyManager keyManager = manager(JwtAlgorithm.RS256);
        JwksService service = service(keyManager);
        String first = service.getJwksJson();
        long afterFirst = service.computeCount();
        String second = service.getJwksJson();
        assertThat(service.computeCount()).as("命中缓存不应重新计算").isEqualTo(afterFirst);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void redisCacheSharedAcrossInstances() {
        JwtKeyManager keyManager = manager(JwtAlgorithm.RS256);
        JwksService first = service(keyManager);
        String json = first.getJwksJson();

        JwksService second = new JwksService(keyManager,
                new JwtSettings(new JwtProperties(), new MapDynamicConfigService()), redis);
        assertThat(second.computeCount()).as("新实例尚未计算").isEqualTo(0);
        assertThat(second.getJwksJson()).isEqualTo(json);
        assertThat(second.computeCount()).as("应命中 Redis 缓存").isEqualTo(0);
    }

    @Test
    void jwksCachedInRedisWithTtl() {
        JwtKeyManager keyManager = manager(JwtAlgorithm.RS256);
        JwksService service = service(keyManager);
        service.getJwksJson();
        Long ttl = redis.getExpire(JwksService.CACHE_KEY, TimeUnit.SECONDS);
        assertThat(ttl).as("缓存必须带 TTL").isNotNull().isPositive();
        assertThat(ttl).isLessThanOrEqualTo(600);
    }

    @Test
    void cacheInvalidatedWithBothKeysAfterRotation() {
        JwtKeyManager keyManager = manager(JwtAlgorithm.RS256);
        JwksService service = service(keyManager);
        String oldKid = keyManager.currentKid();
        String before = service.getJwksJson();
        keyManager.rotate("rotated-jwks-kid");

        String after = service.getJwksJson();
        assertThat(after).isNotEqualTo(before);
        JwkSet parsed = JwkSet.parse(after);
        assertThat(parsed.findByKid(oldKid)).as("旧公钥仍应保留").isNotNull();
        assertThat(parsed.findByKid("rotated-jwks-kid")).as("新公钥必须出现").isNotNull();
    }

    @Test
    void redisOutageDegradesToMemoryWithoutBlocking() {
        JwtKeyManager keyManager = manager(JwtAlgorithm.RS256);
        StringRedisTemplate broken = new StringRedisTemplate() {
            @Override
            public org.springframework.data.redis.core.ValueOperations<String, String> opsForValue() {
                throw new IllegalStateException("Redis 不可用");
            }

            @Override
            public Boolean delete(String key) {
                throw new IllegalStateException("Redis 不可用");
            }
        };
        broken.setConnectionFactory(redis.getConnectionFactory());
        JwksService service = new JwksService(keyManager,
                new JwtSettings(new JwtProperties(), new MapDynamicConfigService()), broken);
        String json = service.getJwksJson();
        assertThat(JwkSet.parse(json).first().kty()).isEqualTo("RSA");
        assertThat(service.computeCount()).isEqualTo(1);
    }

    @Test
    void jwksPublicKeyUsableExternally() {
        JwtKeyManager keyManager = manager(JwtAlgorithm.RS256);
        JwksService service = service(keyManager);
        String token = JwtCodec.encode(JwtAlgorithm.RS256, keyManager.current().signingKey(),
                java.util.Map.of("kid", keyManager.currentKid()),
                java.util.Map.of("sub", "user-1", "iss", "modality", "exp", 9999999999L));

        Jwk jwk = JwkSet.parse(service.getJwksJson()).requireByKid(keyManager.currentKid());
        assertThat(JwtCodec.verify(token, jwk.toPublicKey())).isTrue();
    }
}
