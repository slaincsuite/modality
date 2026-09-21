package cn.slainc.jwt.introspect;

import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.jwt.KeyFixtures;
import cn.slainc.jwt.MapDynamicConfigService;
import cn.slainc.jwt.MutableClock;
import cn.slainc.jwt.algorithm.JwtAlgorithmWhitelist;
import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.issue.SignedToken;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.jwt.revoke.MemoryRevocationStore;
import cn.slainc.jwt.revoke.RevocationService;
import cn.slainc.jwt.verify.JwtVerifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntrospectionServiceTest {

    private final MutableClock clock = new MutableClock(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));

    record Fixture(JwtKeyManager keyManager, JwtTokenService tokenService, RevocationService revocation,
                   JwtVerifier verifier) {

        IntrospectionService introspection() {
            return new IntrospectionService(verifier());
        }
    }

    private Fixture fixture(MutableClock verifierClock) {
        JwtProperties properties = new JwtProperties();
        JwtProperties.KeySpec spec = new JwtProperties.KeySpec();
        spec.setKid("hs-main");
        spec.setAlgorithm(JwtAlgorithm.HS256);
        spec.setSecretBase64(KeyFixtures.secretBase64(32));
        properties.getKeys().add(spec);
        JwtKeyManager manager = new JwtKeyManager(properties);
        JwtAlgorithmWhitelist whitelist = JwtAlgorithmWhitelist.defaults();
        JwtSettings settings = new JwtSettings(properties, new MapDynamicConfigService());
        MemoryRevocationStore store = new MemoryRevocationStore();
        RevocationService revocation = new RevocationService(store);
        JwtTokenService tokenService = new JwtTokenService(manager, whitelist, settings, clock);
        JwtVerifier verifier = new JwtVerifier(manager, whitelist, settings, revocation,
                verifierClock == null ? clock : verifierClock);
        return new Fixture(manager, tokenService, revocation, verifier);
    }

    @Test
    void validTokenReturnsActiveAndClaims() {
        Fixture fixture = fixture(null);
        SignedToken signed = fixture.tokenService().issueToken(
                TokenIssuanceSpec.builder("user-9").claim("dept", "finance").build());
        IntrospectionResult result = fixture.introspection().introspect(signed.tokenValue());
        assertThat(result.active()).isTrue();
        assertThat(result.claims().get("sub")).isEqualTo("user-9");
        assertThat(result.claims().get("dept")).isEqualTo("finance");
        assertThat(result.tokenType()).isEqualTo("ACCESS");
        assertThat(result.exp()).isEqualTo(signed.expiresAt().getEpochSecond());
    }

    @Test
    void expiredTokenReportsInactive() {
        Fixture fixture = fixture(null);
        SignedToken signed = fixture.tokenService().issueToken(
                TokenIssuanceSpec.builder("user-1").ttl(Duration.ofSeconds(10)).build());
        MutableClock later = new MutableClock(clock.instant().plusSeconds(30));
        IntrospectionResult result = fixture(later).introspection().introspect(signed.tokenValue());
        assertThat(result.active()).isFalse();
        assertThat(result.claims()).isEmpty();
        assertThat(result.tokenType()).isNull();
        assertThat(result.exp()).isNull();
    }

    @Test
    void revokedTokenReportsInactive() {
        Fixture fixture = fixture(null);
        SignedToken signed = fixture.tokenService().issueToken(TokenIssuanceSpec.of("user-1"));
        fixture.revocation().store().revoke(signed.jti(), signed.expiresAt());
        assertThat(fixture.introspection().introspect(signed.tokenValue()).active()).isFalse();
    }

    @Test
    void malformedTokenReportsInactive() {
        Fixture fixture = fixture(null);
        assertThat(fixture.introspection().introspect("garbage-token").active()).isFalse();
    }

    @Test
    void externalTokenActiveWithNullType() {
        Fixture fixture = fixture(null);
        String external = JwtCodec.encode(JwtAlgorithm.HS256, fixture.keyManager().current().signingKey(),
                Map.of("kid", "hs-main"),
                Map.of("sub", "ext-user", "iss", "modality", "exp",
                        clock.instant().plusSeconds(600).getEpochSecond(), "jti", "ext-jti"));
        IntrospectionResult result = fixture.introspection().introspect(external);
        assertThat(result.active()).isTrue();
        assertThat(result.tokenType()).isNull();
        assertThat(result.claims().get("sub")).isEqualTo("ext-user");
    }
}
