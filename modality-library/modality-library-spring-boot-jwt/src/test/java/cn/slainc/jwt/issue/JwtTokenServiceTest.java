package cn.slainc.jwt.issue;

import cn.slainc.crypto.jwt.Jwt;
import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.jwt.KeyFixtures;
import cn.slainc.jwt.MutableClock;
import cn.slainc.jwt.algorithm.JwtAlgorithmWhitelist;
import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.jwt.revoke.MemoryRevocationStore;
import cn.slainc.jwt.revoke.RevocationService;
import cn.slainc.jwt.verify.JwtVerification;
import cn.slainc.jwt.verify.JwtVerifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private final MutableClock clock = new MutableClock(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));

    private JwtProperties propertiesFor(JwtAlgorithm algorithm) {
        JwtProperties properties = new JwtProperties();
        JwtProperties.KeySpec spec = new JwtProperties.KeySpec();
        spec.setKid("test-" + algorithm.alg());
        spec.setAlgorithm(algorithm);
        if (algorithm.isHmac()) {
            spec.setSecretBase64(KeyFixtures.secretBase64(32));
        }
        properties.getKeys().add(spec);
        return properties;
    }

    private Env envFor(JwtAlgorithm algorithm) {
        JwtProperties properties = propertiesFor(algorithm);
        JwtKeyManager manager = new JwtKeyManager(properties);
        JwtAlgorithmWhitelist whitelist = new JwtAlgorithmWhitelist(Arrays.asList(JwtAlgorithm.values()));
        JwtSettings settings = new JwtSettings(properties, (cn.slainc.core.config.DynamicConfigService) null);
        JwtTokenService service = new JwtTokenService(manager, whitelist, settings, clock);
        JwtVerifier verifier = new JwtVerifier(manager, whitelist, settings,
                new RevocationService(new MemoryRevocationStore()), clock);
        return new Env(service, verifier);
    }

    private JwtTokenService serviceFor(JwtAlgorithm algorithm) {
        return envFor(algorithm).service();
    }

    record Env(JwtTokenService service, JwtVerifier verifier) {
    }

    private Jwt decode(SignedToken signed) {
        return JwtCodec.decodeUnverified(signed.tokenValue());
    }

    @Test
    void HS256签发后可验签() {
        JwtTokenServiceTest.Env env = envFor(JwtAlgorithm.HS256);
        SignedToken signed = env.service().issueToken(TokenIssuanceSpec.of("user-1"));
        JwtVerification verification = env.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isTrue();
        assertThat(verification.claims().get("sub")).isEqualTo("user-1");
        assertThat(verification.kid()).isEqualTo("test-HS256");
        assertThat(verification.jti()).isEqualTo(signed.jti());
    }

    @Test
    void RS256签发后可验签() {
        JwtTokenServiceTest.Env env = envFor(JwtAlgorithm.RS256);
        SignedToken signed = env.service().issueToken(TokenIssuanceSpec.of("user-2"));
        JwtVerification verification = env.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isTrue();
    }

    @Test
    void ES256签发后可验签() {
        JwtTokenServiceTest.Env env = envFor(JwtAlgorithm.ES256);
        SignedToken signed = env.service().issueToken(TokenIssuanceSpec.of("user-3"));
        JwtVerification verification = env.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isTrue();
    }

    @Test
    void PS256签发后可验签() {
        JwtTokenServiceTest.Env env = envFor(JwtAlgorithm.PS256);
        SignedToken signed = env.service().issueToken(TokenIssuanceSpec.of("user-4"));
        JwtVerification verification = env.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isTrue();
    }

    @Test
    void EdDSA签发后可验签() {
        JwtTokenServiceTest.Env env = envFor(JwtAlgorithm.EdDSA);
        SignedToken signed = env.service().issueToken(TokenIssuanceSpec.of("user-5"));
        JwtVerification verification = env.verifier().verify(signed.tokenValue());
        assertThat(verification.valid()).isTrue();
    }

    @Test
    void 五种算法签出的令牌kid与alg正确() {
        for (JwtAlgorithm algorithm : List.of(JwtAlgorithm.HS256, JwtAlgorithm.RS256,
                JwtAlgorithm.ES256, JwtAlgorithm.PS256, JwtAlgorithm.EdDSA)) {
            SignedToken signed = serviceFor(algorithm).issueToken(TokenIssuanceSpec.of("user-kid"));
            Jwt jwt = decode(signed);
            assertThat(jwt.algorithm()).as("算法 %s", algorithm).isEqualTo(algorithm.alg());
            assertThat(jwt.keyId()).isEqualTo("test-" + algorithm.alg());
            assertThat(jwt.type()).isEqualTo("JWT");
        }
    }

    @Test
    void 标准claim完整写入() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256)
                .issueToken(TokenIssuanceSpec.builder("user-100").audience("client-a").build());
        Jwt jwt = decode(signed);
        assertThat(jwt.issuer()).isEqualTo("modality");
        assertThat(jwt.subject()).isEqualTo("user-100");
        assertThat(jwt.audience()).isEqualTo("client-a");
        assertThat(jwt.issuedAt()).isEqualTo(clock.instant());
        assertThat(jwt.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(2)));
        assertThat(jwt.notBefore()).isNull();
        assertThat(UUID.fromString(jwt.jwtId())).isNotNull();
        assertThat(signed.jti()).isEqualTo(jwt.jwtId());
        assertThat(jwt.claims().get("token_type")).isEqualTo("ACCESS");
        assertThat(signed.tokenType()).isEqualTo(TokenType.ACCESS);
        assertThat(signed.kid()).isEqualTo("test-HS256");
    }

    @Test
    void 自定义claim写入载荷() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.builder("user-1")
                .claim("roles", List.of("admin", "user"))
                .claim("tenantId", "t-001")
                .claim("loginIp", "10.0.0.1")
                .build());
        Map<String, Object> claims = decode(signed).claims();
        assertThat(claims.get("tenantId")).isEqualTo("t-001");
        assertThat(claims.get("loginIp")).isEqualTo("10.0.0.1");
        assertThat((List<Object>) claims.get("roles")).containsExactly("admin", "user");
    }

    @Test
    void 自定义claim不能覆盖标准claim() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.builder("real-sub")
                .claim("sub", "fake-sub")
                .claim("iss", "fake-issuer")
                .build());
        Jwt jwt = decode(signed);
        assertThat(jwt.subject()).isEqualTo("real-sub");
        assertThat(jwt.issuer()).isEqualTo("modality");
    }

    @Test
    void 多受众写成数组() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.builder("user-1")
                .audience("client-a")
                .audience("client-b")
                .build());
        assertThat((List<Object>) decode(signed).claims().get("aud")).containsExactly("client-a", "client-b");
    }

    @Test
    void 单受众写成字符串() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.builder("user-1")
                .audience("client-a")
                .build());
        assertThat(decode(signed).audience()).isEqualTo("client-a");
    }

    @Test
    void ACCESS默认TTL两小时() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.of("user-1"));
        assertThat(Duration.between(decode(signed).issuedAt(), decode(signed).expiresAt()))
                .isEqualTo(Duration.ofHours(2));
        assertThat(signed.tokenType().defaultTtl()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void REFRESH默认TTL三十天() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256)
                .issueToken(TokenIssuanceSpec.builder("user-1").tokenType(TokenType.REFRESH).build());
        assertThat(Duration.between(decode(signed).issuedAt(), decode(signed).expiresAt()))
                .isEqualTo(Duration.ofDays(30));
    }

    @Test
    void ID_TOKEN默认TTL十分钟() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256)
                .issueToken(TokenIssuanceSpec.builder("user-1").tokenType(TokenType.ID_TOKEN).build());
        assertThat(Duration.between(decode(signed).issuedAt(), decode(signed).expiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void ONE_SHOT默认TTL五分钟() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256)
                .issueToken(TokenIssuanceSpec.builder("user-1").tokenType(TokenType.ONE_SHOT).build());
        assertThat(Duration.between(decode(signed).issuedAt(), decode(signed).expiresAt()))
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void ID_TOKEN按OIDC写入nonce与auth_time() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.builder("user-1")
                .tokenType(TokenType.ID_TOKEN)
                .nonce("nonce-abc")
                .authTime(1750000000L)
                .acr("urn:mace:incommon:iap:silver")
                .amr(List.of("pwd", "otp"))
                .azp("client-a")
                .build());
        Map<String, Object> claims = decode(signed).claims();
        assertThat(claims.get("nonce")).isEqualTo("nonce-abc");
        assertThat(claims.get("auth_time")).isEqualTo(1750000000L);
        assertThat(claims.get("acr")).isEqualTo("urn:mace:incommon:iap:silver");
        assertThat((List<Object>) claims.get("amr")).containsExactly("pwd", "otp");
        assertThat(claims.get("azp")).isEqualTo("client-a");
    }

    @Test
    void ID_TOKEN未传nonce时不写nonce() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256)
                .issueToken(TokenIssuanceSpec.builder("user-1").tokenType(TokenType.ID_TOKEN).build());
        assertThat(decode(signed).claims()).doesNotContainKey("nonce");
    }

    @Test
    void 动态TTL修改后新签发的令牌立即生效() {
        JwtProperties properties = propertiesFor(JwtAlgorithm.HS256);
        cn.slainc.jwt.MapDynamicConfigService dynamic = new cn.slainc.jwt.MapDynamicConfigService();
        JwtKeyManager manager = new JwtKeyManager(properties);
        JwtAlgorithmWhitelist whitelist = JwtAlgorithmWhitelist.defaults();
        JwtSettings settings = new JwtSettings(properties, (cn.slainc.core.config.DynamicConfigService) dynamic);
        JwtTokenService service = new JwtTokenService(manager, whitelist, settings, clock);
        SignedToken before = service.issueToken(TokenIssuanceSpec.of("user-1"));
        assertThat(Duration.between(decode(before).issuedAt(), decode(before).expiresAt()))
                .isEqualTo(Duration.ofHours(2));

        dynamic.set(cn.slainc.jwt.config.JwtSettings.KEY_TTL_ACCESS, "111");
        SignedToken after = service.issueToken(TokenIssuanceSpec.of("user-1"));
        assertThat(Duration.between(decode(after).issuedAt(), decode(after).expiresAt()))
                .as("改动态 TTL 后新令牌必须立即生效")
                .isEqualTo(Duration.ofSeconds(111));
    }

    @Test
    void spec显式ttl优先于默认值() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.builder("user-1")
                .ttl(Duration.ofSeconds(60))
                .build());
        assertThat(Duration.between(decode(signed).issuedAt(), decode(signed).expiresAt()))
                .isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void spec绝对过期时间覆盖ttl() {
        Instant expiresAt = clock.instant().plusSeconds(3600);
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.builder("user-1")
                .expiresAt(expiresAt)
                .build());
        assertThat(decode(signed).expiresAt()).isEqualTo(expiresAt);
        assertThat(signed.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void nbf延迟按配置写入() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.builder("user-1")
                .notBeforeDelay(Duration.ofSeconds(30))
                .build());
        assertThat(decode(signed).notBefore()).isEqualTo(clock.instant().plusSeconds(30));
    }

    @Test
    void jti可显式指定() {
        SignedToken signed = serviceFor(JwtAlgorithm.HS256).issueToken(TokenIssuanceSpec.builder("user-1")
                .jti("fixed-jti-001")
                .build());
        assertThat(signed.jti()).isEqualTo("fixed-jti-001");
        assertThat(decode(signed).jwtId()).isEqualTo("fixed-jti-001");
    }

    @Test
    void issueBatch批量签发互不影响() {
        List<TokenIssuanceSpec> specs = List.of(
                TokenIssuanceSpec.builder("u-1").tokenType(TokenType.ACCESS).build(),
                TokenIssuanceSpec.builder("u-2").tokenType(TokenType.REFRESH).build(),
                TokenIssuanceSpec.builder("u-3").tokenType(TokenType.ONE_SHOT).build());
        List<SignedToken> tokens = serviceFor(JwtAlgorithm.HS256).issueBatch(specs);
        assertThat(tokens).hasSize(3);
        assertThat(tokens.get(0).tokenType()).isEqualTo(TokenType.ACCESS);
        assertThat(tokens.get(1).tokenType()).isEqualTo(TokenType.REFRESH);
        assertThat(tokens.get(2).tokenType()).isEqualTo(TokenType.ONE_SHOT);
        assertThat(tokens.stream().map(SignedToken::jti).distinct().count()).isEqualTo(3);
    }

    @Test
    void 空subject构造即拒绝() {
        assertThatThrownBy(() -> TokenIssuanceSpec.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");
    }
}
