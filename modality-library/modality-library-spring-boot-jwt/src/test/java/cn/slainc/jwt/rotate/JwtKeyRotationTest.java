package cn.slainc.jwt.rotate;

import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.crypto.jwt.Jwk;
import cn.slainc.crypto.jwt.JwkSet;
import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.jwt.KeyFixtures;
import cn.slainc.jwt.MapDynamicConfigService;
import cn.slainc.jwt.algorithm.JwtAlgorithmWhitelist;
import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.issue.SignedToken;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.jwks.JwksService;
import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.jwt.revoke.MemoryRevocationStore;
import cn.slainc.jwt.revoke.RevocationService;
import cn.slainc.jwt.verify.JwtVerifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JwtKeyRotationTest {

    private JwtKeyManager manager() {
        JwtProperties properties = new JwtProperties();
        JwtProperties.KeySpec spec = new JwtProperties.KeySpec();
        spec.setKid("rotation-rsa");
        spec.setAlgorithm(JwtAlgorithm.RS256);
        properties.getKeys().add(spec);
        properties.setRetainedKeys(3);
        return new JwtKeyManager(properties);
    }

    private JwtTokenService service(JwtKeyManager keyManager) {
        return new JwtTokenService(keyManager, JwtAlgorithmWhitelist.defaults(),
                new JwtSettings(new JwtProperties(), new MapDynamicConfigService()));
    }

    private JwtVerifier verifier(JwtKeyManager keyManager) {
        return new JwtVerifier(keyManager, JwtAlgorithmWhitelist.defaults(),
                new JwtSettings(new JwtProperties(), new MapDynamicConfigService()),
                new RevocationService(new MemoryRevocationStore()));
    }

    @Test
    void newTokenUsesNewKidAfterRotation() {
        JwtKeyManager keyManager = manager();
        JwtTokenService tokenService = service(keyManager);
        SignedToken before = tokenService.issueToken(TokenIssuanceSpec.of("user-1"));
        assertThat(before.kid()).isEqualTo("rotation-rsa");

        keyManager.rotate("rotated-a");
        SignedToken after = tokenService.issueToken(TokenIssuanceSpec.of("user-1"));
        assertThat(after.kid()).isEqualTo("rotated-a");
        assertThat(JwtCodec.decodeUnverified(after.tokenValue()).keyId()).isEqualTo("rotated-a");
    }

    @Test
    void oldTokenStillVerifiesAfterRotation() {
        JwtKeyManager keyManager = manager();
        JwtTokenService tokenService = service(keyManager);
        JwtVerifier verifier = verifier(keyManager);
        SignedToken oldToken = tokenService.issueToken(TokenIssuanceSpec.of("user-1"));

        keyManager.rotate("rotated-b");
        assertThat(verifier.verify(oldToken.tokenValue()).valid())
                .as("旧令牌必须仍可验签").isTrue();
    }

    @Test
    void jwksContainsBothKeysAfterRotation() {
        JwtKeyManager keyManager = manager();
        JwksService jwksService = new JwksService(keyManager,
                new JwtSettings(new JwtProperties(), new MapDynamicConfigService()), null);
        String oldKid = keyManager.currentKid();
        jwksService.getJwksJson();

        keyManager.rotate("rotated-c");
        JwkSet jwkSet = JwkSet.parse(jwksService.getJwksJson());
        assertThat(jwkSet.findByKid(oldKid)).as("旧公钥").isNotNull();
        assertThat(jwkSet.findByKid("rotated-c")).as("新公钥").isNotNull();
        assertThat(jwkSet.size()).isEqualTo(2);
        for (Jwk jwk : jwkSet.keys()) {
            assertThat(jwk.isPrivate()).as("JWKS 绝不能包含私钥参数").isFalse();
        }
    }

    @Test
    void multipleRotationsKeepOldKidsVerifiable() {
        JwtKeyManager keyManager = manager();
        JwtTokenService tokenService = service(keyManager);
        JwtVerifier verifier = verifier(keyManager);
        SignedToken k0 = tokenService.issueToken(TokenIssuanceSpec.of("user-1"));
        keyManager.rotate("r1");
        SignedToken k1 = tokenService.issueToken(TokenIssuanceSpec.of("user-1"));
        keyManager.rotate("r2");
        SignedToken k2 = tokenService.issueToken(TokenIssuanceSpec.of("user-1"));
        keyManager.rotate("r3");

        assertThat(verifier.verify(k2.tokenValue()).valid()).isTrue();
        assertThat(verifier.verify(k1.tokenValue()).valid()).isTrue();
        assertThat(verifier.verify(k0.tokenValue()).valid())
                .as("超出保留上限的最老密钥应被清除").isFalse();
    }

    @Test
    void schedulerRotatesPeriodically() throws Exception {
        JwtKeyManager keyManager = manager();
        String initialKid = keyManager.currentKid();
        JwtKeyRotationScheduler scheduler = new JwtKeyRotationScheduler(keyManager, Duration.ofMillis(120));
        scheduler.afterPropertiesSet();
        try {
            long deadline = System.currentTimeMillis() + 5000;
            while (keyManager.currentKid().equals(initialKid) && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertThat(keyManager.currentKid()).as("调度器必须完成自动轮换").isNotEqualTo(initialKid);
        } finally {
            scheduler.destroy();
        }
    }

    @Test
    void schedulerDelaysFirstRotation() throws Exception {
        JwtKeyManager keyManager = manager();
        String initialKid = keyManager.currentKid();
        JwtKeyRotationScheduler scheduler = new JwtKeyRotationScheduler(keyManager, Duration.ofMillis(300));
        scheduler.afterPropertiesSet();
        try {
            Thread.sleep(120);
            assertThat(keyManager.currentKid())
                    .as("启动后一个周期内不应轮换（启动期禁 I/O）").isEqualTo(initialKid);
            long deadline = System.currentTimeMillis() + 5000;
            while (keyManager.currentKid().equals(initialKid) && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertThat(keyManager.currentKid()).isNotEqualTo(initialKid);
        } finally {
            scheduler.destroy();
        }
    }

    @Test
    void rotationTriggersJwksCacheInvalidation() {
        JwtKeyManager keyManager = manager();
        AtomicInteger rotations = new AtomicInteger();
        keyManager.onRotation(rotations::incrementAndGet);
        keyManager.rotate("listener-kid");
        keyManager.rotate("listener-kid-2");
        assertThat(rotations.get()).isEqualTo(2);
    }

    @Test
    void hsKeyRotationKeepsJwksEmpty() {
        JwtProperties properties = new JwtProperties();
        JwtProperties.KeySpec spec = new JwtProperties.KeySpec();
        spec.setKid("hs-rotation");
        spec.setAlgorithm(JwtAlgorithm.HS256);
        spec.setSecretBase64(KeyFixtures.secretBase64(32));
        properties.getKeys().add(spec);
        JwtKeyManager keyManager = new JwtKeyManager(properties);
        JwksService jwksService = new JwksService(keyManager,
                new JwtSettings(new JwtProperties(), new MapDynamicConfigService()), null);
        assertThat(jwksService.getJwksJson()).contains("[]");
        keyManager.rotate("hs-rotated");
        assertThat(JwkSet.parse(jwksService.getJwksJson()).isEmpty()).isTrue();
    }
}
