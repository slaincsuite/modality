package cn.slainc.jwt.revoke;

import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.jwt.KeyFixtures;
import cn.slainc.jwt.MapDynamicConfigService;
import cn.slainc.jwt.algorithm.JwtAlgorithmWhitelist;
import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.issue.SignedToken;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.jwt.verify.JwtVerifier;
import cn.slainc.jwt.verify.JwtVerifyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RevocationTokenVerificationTest {

    @Test
    void tamperedTokenIsRejectedAndNotRevoked() {
        Fixture fixture = fixture();
        SignedToken token = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .ttl(Duration.ofMinutes(30)).build());
        assertThatThrownBy(() -> fixture.revocation().revokeByToken(tamperSubject(token.tokenValue())))
                .isInstanceOf(JwtVerifyException.class);
        assertThat(fixture.store().isRevoked(token.jti())).as("验签失败的令牌不能产生吊销记录").isFalse();
    }

    @Test
    void validTokenIsRevokedByJti() {
        Fixture fixture = fixture();
        SignedToken token = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .ttl(Duration.ofMinutes(30)).build());
        fixture.revocation().revokeByToken(token.tokenValue());
        assertThat(fixture.store().isRevoked(token.jti())).isTrue();
        assertThat(fixture.verifier().verify(token.tokenValue()).valid()).isFalse();
    }

    @Test
    void tokenDerivedTtlIsClampedToMaximum() {
        Fixture fixture = fixture();
        SignedToken token = fixture.service().issueToken(TokenIssuanceSpec.builder("user-1")
                .ttl(Duration.ofDays(30)).build());
        fixture.revocation().revokeByToken(token.tokenValue());
        Instant upperBound = Instant.now().plus(RevocationService.MAX_REVOCATION_TTL).plusSeconds(10);
        assertThat(fixture.store().expiresAt(token.jti())).isBeforeOrEqualTo(upperBound);
    }

    @Test
    void revokeWithoutVerifierIsRejected() {
        MemoryRevocationStore store = new MemoryRevocationStore();
        RevocationService service = new RevocationService(store);
        assertThatThrownBy(() -> service.revokeByToken("a.b.c"))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("验签");
        assertThat(store.size()).isZero();
    }

    private Fixture fixture() {
        JwtProperties properties = new JwtProperties();
        JwtProperties.KeySpec spec = new JwtProperties.KeySpec();
        spec.setKid("hs-main");
        spec.setAlgorithm(JwtAlgorithm.HS256);
        spec.setSecretBase64(KeyFixtures.secretBase64(32));
        properties.getKeys().add(spec);
        JwtKeyManager manager = new JwtKeyManager(properties);
        JwtAlgorithmWhitelist whitelist = JwtAlgorithmWhitelist.defaults();
        JwtSettings settings = new JwtSettings(properties, new MapDynamicConfigService());
        CapturingRevocationStore store = new CapturingRevocationStore();
        RevocationService base = new RevocationService(store);
        JwtTokenService tokenService = new JwtTokenService(manager, whitelist, settings);
        JwtVerifier verifier = new JwtVerifier(manager, whitelist, settings, base);
        RevocationService secured = new RevocationService(store, verifier::verifyStrict);
        return new Fixture(tokenService, verifier, secured, store);
    }

    private String tamperSubject(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("user-1", "user-2").getBytes(StandardCharsets.UTF_8));
        return String.join(".", parts);
    }

    private record Fixture(JwtTokenService service, JwtVerifier verifier, RevocationService revocation,
                           CapturingRevocationStore store) {
    }

    private static final class CapturingRevocationStore implements RevocationStore {

        private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

        @Override
        public void revoke(String jti, Instant expiresAt) {
            revoked.put(jti, expiresAt);
        }

        @Override
        public boolean isRevoked(String jti) {
            return revoked.containsKey(jti);
        }

        Instant expiresAt(String jti) {
            return revoked.get(jti);
        }
    }
}
