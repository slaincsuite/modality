package cn.slainc.jwt.verify;

import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.jwt.KeyFixtures;
import cn.slainc.jwt.MapDynamicConfigService;
import cn.slainc.jwt.MutableClock;
import cn.slainc.jwt.algorithm.JwtAlgorithmWhitelist;
import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.issue.TokenType;
import cn.slainc.jwt.key.JwtKeyEntry;
import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.jwt.revoke.MemoryRevocationStore;
import cn.slainc.jwt.revoke.RevocationService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierTest {

    private final MutableClock clock = new MutableClock(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));

    private JwtProperties hsProperties() {
        JwtProperties properties = new JwtProperties();
        properties.getKeys().add(hsKeySpec());
        return properties;
    }

    private JwtProperties.KeySpec hsKeySpec() {
        JwtProperties.KeySpec spec = new JwtProperties.KeySpec();
        spec.setKid("hs-main");
        spec.setAlgorithm(JwtAlgorithm.HS256);
        spec.setSecretBase64(KeyFixtures.secretBase64(32));
        return spec;
    }

    private JwtProperties dualProperties() {
        JwtProperties properties = hsProperties();
        JwtProperties.KeySpec rsa = new JwtProperties.KeySpec();
        rsa.setKid("rsa-main");
        rsa.setAlgorithm(JwtAlgorithm.RS256);
        properties.getKeys().add(rsa);
        return properties;
    }

    private VerifierFixture fixture() {
        return fixture(hsProperties(), JwtAlgorithmWhitelist.defaults());
    }

    private VerifierFixture dualFixture() {
        return fixture(dualProperties(), JwtAlgorithmWhitelist.defaults());
    }

    private VerifierFixture fixture(JwtProperties properties, JwtAlgorithmWhitelist whitelist) {
        JwtKeyManager manager = new JwtKeyManager(properties);
        MapDynamicConfigService dynamic = new MapDynamicConfigService();
        JwtSettings settings = new JwtSettings(properties, (cn.slainc.core.config.DynamicConfigService) dynamic);
        RevocationService revocation = new RevocationService(new MemoryRevocationStore());
        JwtTokenService service = new JwtTokenService(manager, JwtAlgorithmWhitelist.defaults(), settings, clock);
        JwtVerifier verifier = new JwtVerifier(manager, whitelist, settings, revocation, clock);
        return new VerifierFixture(manager, service, verifier, revocation, dynamic);
    }

    record VerifierFixture(JwtKeyManager manager, JwtTokenService service, JwtVerifier verifier,
                           RevocationService revocation, MapDynamicConfigService dynamic) {

        JwtKeyEntry hsKey() {
            return manager().requireByKid("hs-main");
        }
    }

    @Test
    void 有效令牌返回完整验证结果() {
        VerifierFixture fixture = fixture();
        var signed = fixture.service().issueToken(TokenIssuanceSpec.of("user-1"));
        JwtVerification verification = fixture.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isTrue();
        assertThat(verification.error()).isNull();
        assertThat(verification.claims().get("sub")).isEqualTo("user-1");
        assertThat(verification.kid()).isEqualTo("hs-main");
        assertThat(verification.tokenType()).isEqualTo(TokenType.ACCESS);
        assertThat(verification.jti()).isEqualTo(signed.jti());
    }

    @Test
    void 过期令牌在零偏移下被拒绝() {
        VerifierFixture fixture = fixture();
        fixture.dynamic().set(cn.slainc.jwt.config.JwtSettings.KEY_CLOCK_SKEW_SECONDS, "0");
        var signed = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .ttl(Duration.ofSeconds(60)).build());
        clock.advance(Duration.ofSeconds(61));
        JwtVerification verification = fixture.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("过期");
    }

    @Test
    void 默认时钟偏移容忍刚过期的令牌() {
        VerifierFixture fixture = fixture();
        var signed = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .ttl(Duration.ofSeconds(60)).build());
        clock.advance(Duration.ofSeconds(100));
        assertThat(fixture.verifier().verify(signed.tokenValue()).valid()).isTrue();
    }

    @Test
    void 动态clockSkew热读后立即生效() {
        VerifierFixture fixture = fixture();
        var signed = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .ttl(Duration.ofSeconds(60)).build());
        clock.advance(Duration.ofSeconds(100));
        assertThat(fixture.verifier().verify(signed.tokenValue()).valid()).isTrue();
        fixture.dynamic().set(cn.slainc.jwt.config.JwtSettings.KEY_CLOCK_SKEW_SECONDS, "0");
        JwtVerification verification = fixture.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("过期");
    }

    @Test
    void nbf未生效的令牌被拒绝() {
        VerifierFixture fixture = fixture();
        var signed = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .notBeforeDelay(Duration.ofSeconds(300)).build());
        JwtVerification verification = fixture.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("尚未生效");
    }

    @Test
    void nbf到期后令牌通过验证() {
        VerifierFixture fixture = fixture();
        var signed = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .notBeforeDelay(Duration.ofSeconds(300)).build());
        clock.advance(Duration.ofSeconds(301));
        assertThat(fixture.verifier().verify(signed.tokenValue()).valid()).isTrue();
    }

    @Test
    void iss不匹配被拒绝() {
        VerifierFixture fixture = fixture();
        String forged = JwtCodec.encode(JwtAlgorithm.HS256,
                fixture.hsKey().signingKey(),
                Map.of("typ", "JWT", "kid", "hs-main"),
                Map.of("sub", "user-1", "iss", "other-issuer", "exp",
                        clock.instant().plusSeconds(600).getEpochSecond(), "jti", "j-1"));
        JwtVerification verification = fixture.verifier().verify(forged);
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("iss").contains("other-issuer");
    }

    @Test
    void 缺少iss被拒绝() {
        VerifierFixture fixture = fixture();
        String forged = JwtCodec.encode(JwtAlgorithm.HS256,
                fixture.hsKey().signingKey(),
                Map.of("typ", "JWT", "kid", "hs-main"),
                Map.of("sub", "user-1", "exp", clock.instant().plusSeconds(600).getEpochSecond()));
        JwtVerification verification = fixture.verifier().verify(forged);
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("iss");
    }

    @Test
    void aud不匹配被拒绝() {
        JwtProperties properties = hsProperties();
        properties.setAudiences(List.of("client-a"));
        VerifierFixture fixture = fixture(properties, JwtAlgorithmWhitelist.defaults());
        String forged = JwtCodec.encode(JwtAlgorithm.HS256,
                fixture.hsKey().signingKey(),
                Map.of("typ", "JWT", "kid", "hs-main"),
                Map.of("sub", "user-1", "iss", "modality", "aud", "client-b",
                        "exp", clock.instant().plusSeconds(600).getEpochSecond(), "jti", "j-2"));
        JwtVerification verification = fixture.verifier().verify(forged);
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("受众");
    }

    @Test
    void aud命中配置即通过() {
        JwtProperties properties = hsProperties();
        properties.setAudiences(List.of("client-a", "client-b"));
        VerifierFixture fixture = fixture(properties, JwtAlgorithmWhitelist.defaults());
        String forged = JwtCodec.encode(JwtAlgorithm.HS256,
                fixture.hsKey().signingKey(),
                Map.of("typ", "JWT", "kid", "hs-main"),
                Map.of("sub", "user-1", "iss", "modality", "aud", List.of("client-b"),
                        "exp", clock.instant().plusSeconds(600).getEpochSecond(), "jti", "j-3"));
        assertThat(fixture.verifier().verify(forged).valid()).isTrue();
    }

    @Test
    void 未知kid按中文异常拒绝() {
        VerifierFixture fixture = fixture();
        String forged = JwtCodec.encode(JwtAlgorithm.HS256,
                fixture.hsKey().signingKey(),
                Map.of("typ", "JWT", "kid", "no-such-kid"),
                Map.of("sub", "user-1", "iss", "modality"));
        assertThatThrownBy(() -> fixture.verifier().verifyStrict(forged))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("no-such-kid");
        assertThat(fixture.verifier().verify(forged).valid()).isFalse();
    }

    @Test
    void alg为none的令牌一律拒绝() {
        VerifierFixture fixture = fixture();
        String payload = base64Url("{\"sub\":\"user-1\",\"iss\":\"modality\",\"exp\":9999999999}");
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String token = header + "." + payload + "." + base64Url("placeholder-signature");
        assertThatThrownBy(() -> fixture.verifier().verifyStrict(token))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("none");
        assertThat(fixture.verifier().verify(token).valid()).isFalse();
    }

    @Test
    void 白名单外算法的令牌被拒绝() {
        VerifierFixture fixture = fixture();
        javax.crypto.SecretKey hs512Key = cn.slainc.crypto.key.SecretKeyUtils.hmacKey("HmacSHA512", 64);
        String forged = JwtCodec.encode(JwtAlgorithm.HS512, hs512Key,
                Map.of("typ", "JWT", "kid", "hs-main"),
                Map.of("sub", "user-1", "iss", "modality", "exp", 9999999999L));
        assertThatThrownBy(() -> fixture.verifier().verifyStrict(forged))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("白名单");
        assertThat(fixture.verifier().verify(forged).valid()).isFalse();
    }

    @Test
    void 算法混淆的令牌被拒绝() {
        VerifierFixture fixture = dualFixture();
        assertThat(fixture.manager().currentKid()).isEqualTo("rsa-main");
        javax.crypto.SecretKey attackerKey = cn.slainc.crypto.key.SecretKeyUtils.hmacKey("HmacSHA256", 32);
        String forged = JwtCodec.encode(JwtAlgorithm.HS256, attackerKey,
                Map.of("typ", "JWT", "kid", "rsa-main"),
                Map.of("sub", "user-1", "iss", "modality", "exp", 9999999999L));
        assertThatThrownBy(() -> fixture.verifier().verifyStrict(forged))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("混淆");
        assertThat(fixture.verifier().verify(forged).valid()).isFalse();
    }

    @Test
    void 对称密钥冒用非对称kid同样被拒绝() {
        VerifierFixture fixture = dualFixture();
        String forged = JwtCodec.encode(JwtAlgorithm.HS256, fixture.hsKey().signingKey(),
                Map.of("typ", "JWT", "kid", "rsa-main"),
                Map.of("sub", "user-1", "iss", "modality", "exp", 9999999999L));
        assertThat(fixture.verifier().verify(forged).valid()).isFalse();
    }

    @Test
    void 签名被篡改的令牌被拒绝() {
        VerifierFixture fixture = fixture();
        var signed = fixture.service().issueToken(TokenIssuanceSpec.of("user-1"));
        String[] parts = signed.tokenValue().split("\\.");
        char[] signature = parts[2].toCharArray();
        signature[0] = signature[0] == 'A' ? 'B' : 'A';
        String tamperedToken = parts[0] + "." + parts[1] + "." + new String(signature);
        JwtVerification verification = fixture.verifier().verify(tamperedToken);
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("签名");
    }

    @Test
    void 超长令牌被拒绝() {
        JwtProperties properties = hsProperties();
        properties.setMaxTokenLength(80);
        VerifierFixture fixture = fixture(properties, JwtAlgorithmWhitelist.defaults());
        var signed = fixture.service().issueToken(TokenIssuanceSpec.of("user-1"));
        assertThat(signed.tokenValue().length()).isGreaterThan(80);
        assertThatThrownBy(() -> fixture.verifier().verifyStrict(signed.tokenValue()))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("上限")
                .hasMessageContaining("80");
    }

    @Test
    void 动态maxTokenLength热读后立即生效() {
        VerifierFixture fixture = fixture();
        var signed = fixture.service().issueToken(TokenIssuanceSpec.of("user-1"));
        assertThat(fixture.verifier().verify(signed.tokenValue()).valid()).isTrue();
        fixture.dynamic().set(cn.slainc.jwt.config.JwtSettings.KEY_MAX_TOKEN_LENGTH, "100");
        JwtVerification verification = fixture.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("100");
    }

    @Test
    void 吊销后的令牌被拒绝() {
        VerifierFixture fixture = fixture();
        var signed = fixture.service().issueToken(TokenIssuanceSpec.of("user-1"));
        assertThat(fixture.verifier().verify(signed.tokenValue()).valid()).isTrue();
        fixture.revocation().revokeByToken(signed.tokenValue());
        JwtVerification verification = fixture.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("吊销");
    }

    @Test
    void 格式非法的令牌被拒绝() {
        VerifierFixture fixture = fixture();
        JwtVerification verification = fixture.verifier().verify("not-a-jwt");
        assertThat(verification.valid()).isFalse();
        assertThat(verification.error()).contains("格式非法");
        assertThat(fixture.verifier().verify(null).valid()).isFalse();
        assertThat(fixture.verifier().verify("").valid()).isFalse();
    }

    @Test
    void 空白令牌verifyStrict抛出中文异常() {
        VerifierFixture fixture = fixture();
        assertThatThrownBy(() -> fixture.verifier().verifyStrict(" "))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("不能为空");
    }

    private String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes());
    }
}
