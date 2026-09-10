package cn.slainc.jwt.refresh;

import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.jwt.KeyFixtures;
import cn.slainc.jwt.MapDynamicConfigService;
import cn.slainc.jwt.MutableClock;
import cn.slainc.jwt.algorithm.JwtAlgorithmWhitelist;
import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.issue.TokenType;
import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.jwt.revoke.MemoryRevocationStore;
import cn.slainc.jwt.revoke.RevocationService;
import cn.slainc.jwt.verify.JwtVerifier;
import cn.slainc.jwt.verify.JwtVerifyException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenServiceTest {

    private final MutableClock clock = new MutableClock(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));

    private RefreshFixture fixture(boolean rotation) {
        JwtProperties properties = new JwtProperties();
        JwtProperties.KeySpec spec = new JwtProperties.KeySpec();
        spec.setKid("hs-main");
        spec.setAlgorithm(JwtAlgorithm.HS256);
        spec.setSecretBase64(KeyFixtures.secretBase64(32));
        properties.getKeys().add(spec);
        properties.setRefreshRotation(rotation);
        JwtKeyManager manager = new JwtKeyManager(properties);
        JwtAlgorithmWhitelist whitelist = JwtAlgorithmWhitelist.defaults();
        JwtSettings settings = new JwtSettings(properties, new MapDynamicConfigService());
        RevocationService revocation = new RevocationService(new MemoryRevocationStore());
        JwtTokenService service = new JwtTokenService(manager, whitelist, settings, clock);
        JwtVerifier verifier = new JwtVerifier(manager, whitelist, settings, revocation, clock);
        RefreshTokenService refreshTokenService = new RefreshTokenService(verifier, service, revocation, settings);
        return new RefreshFixture(service, revocation, refreshTokenService);
    }

    record RefreshFixture(JwtTokenService service, RevocationService revocation, RefreshTokenService refresh) {
    }

    private String refreshOf(RefreshFixture fixture) {
        return fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .tokenType(TokenType.REFRESH)
                .audience("client-a")
                .claim("roles", List.of("admin"))
                .build()).tokenValue();
    }

    @Test
    void 刷新令牌成功换发新的ACCESS与REFRESH() {
        RefreshFixture fixture = fixture(true);
        String refreshToken = refreshOf(fixture);
        RefreshedTokens refreshed = fixture.refresh().refresh(refreshToken,
                TokenIssuanceSpec.builder("user-1").audience("client-a").build());
        assertThat(refreshed.accessToken().tokenType()).isEqualTo(TokenType.ACCESS);
        assertThat(refreshed.refreshToken().tokenType()).isEqualTo(TokenType.REFRESH);
        assertThat(refreshed.accessToken().jti()).isNotEqualTo(refreshed.refreshToken().jti());
        assertThat(refreshed.accessToken().expiresAt()).isBefore(refreshed.refreshToken().expiresAt());
    }

    @Test
    void 换发后的新REFRESH可继续换发() {
        RefreshFixture fixture = fixture(true);
        String firstRefresh = refreshOf(fixture);
        RefreshedTokens first = fixture.refresh().refresh(firstRefresh,
                TokenIssuanceSpec.builder("user-1").build());
        RefreshedTokens second = fixture.refresh().refresh(first.refreshToken().tokenValue(),
                TokenIssuanceSpec.builder("user-1").build());
        assertThat(second.accessToken().jti()).isNotEqualTo(first.accessToken().jti());
    }

    @Test
    void rotation默认开启旧REFRESH复用被拒绝() {
        RefreshFixture fixture = fixture(true);
        String oldRefresh = refreshOf(fixture);
        fixture.refresh().refresh(oldRefresh, TokenIssuanceSpec.builder("user-1").build());
        assertThatThrownBy(() -> fixture.refresh().refresh(oldRefresh,
                TokenIssuanceSpec.builder("user-1").build()))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("无效");
        assertThat(fixture.revocation().isRevoked(jtiOf(oldRefresh))).isTrue();
    }

    private String jtiOf(String token) {
        return cn.slainc.crypto.jwt.JwtCodec.decodeUnverified(token).jwtId();
    }

    @Test
    void ACCESS令牌调用refresh给出中文异常() {
        RefreshFixture fixture = fixture(true);
        String accessToken = fixture.service().issueToken(TokenIssuanceSpec.of("user-1")).tokenValue();
        assertThatThrownBy(() -> fixture.refresh().refresh(accessToken,
                TokenIssuanceSpec.builder("user-1").build()))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("仅刷新令牌")
                .hasMessageContaining("ACCESS");
    }

    @Test
    void ONE_SHOT令牌调用refresh同样被拒绝() {
        RefreshFixture fixture = fixture(true);
        String oneShot = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .tokenType(TokenType.ONE_SHOT).build()).tokenValue();
        assertThatThrownBy(() -> fixture.refresh().refresh(oneShot,
                TokenIssuanceSpec.builder("user-1").build()))
                .hasMessageContaining("ONE_SHOT");
    }

    @Test
    void 关闭rotation后旧REFRESH仍可复用() {
        RefreshFixture fixture = fixture(false);
        String refreshToken = refreshOf(fixture);
        RefreshedTokens first = fixture.refresh().refresh(refreshToken,
                TokenIssuanceSpec.builder("user-1").build());
        RefreshedTokens second = fixture.refresh().refresh(refreshToken,
                TokenIssuanceSpec.builder("user-1").build());
        assertThat(first.refreshToken().jti()).isNotEqualTo(second.refreshToken().jti());
        assertThat(fixture.revocation().isRevoked(jtiOf(refreshToken))).isFalse();
    }

    @Test
    void 换发的ACCESS保留受众与自定义claim() {
        RefreshFixture fixture = fixture(true);
        String refreshToken = refreshOf(fixture);
        RefreshedTokens refreshed = fixture.refresh().refresh(refreshToken,
                TokenIssuanceSpec.builder("user-1").audience("client-a").claim("roles", List.of("admin")).build());
        var claims = cn.slainc.crypto.jwt.JwtCodec.decodeUnverified(refreshed.accessToken().tokenValue()).claims();
        assertThat(claims.get("aud")).isEqualTo("client-a");
        assertThat((List<Object>) claims.get("roles")).containsExactly("admin");
    }

    @Test
    void 过期的REFRESH换发失败() {
        RefreshFixture fixture = fixture(true);
        var signed = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .tokenType(TokenType.REFRESH)
                .ttl(Duration.ofSeconds(60)).build());
        clock.advance(Duration.ofMinutes(5));
        assertThatThrownBy(() -> fixture.refresh().refresh(signed.tokenValue(),
                TokenIssuanceSpec.builder("user-1").build()))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("无效")
                .hasMessageContaining("过期");
    }
}
