package cn.slainc.oauth2;

import cn.dev33.satoken.oauth2.exception.SaOAuth2Exception;
import cn.slainc.oauth2.pkce.PkceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkceServiceTest {

    private final PkceService pkceService = new PkceService(null);

    @Test
    void s256ChallengeVerifiesAndConsumesOnce() {
        String verifier = pkceService.randomVerifier();
        String challenge = pkceService.challengeOf(PkceService.METHOD_S256, verifier);
        pkceService.saveChallenge("code-1", PkceService.METHOD_S256, challenge, 60);

        assertThat(pkceService.hasChallenge("code-1")).isTrue();
        pkceService.verifyAndConsume("code-1", verifier);
        assertThat(pkceService.hasChallenge("code-1")).isFalse();
    }

    @Test
    void mismatchedVerifierIsRejected() {
        String challenge = pkceService.challengeOf(PkceService.METHOD_S256, pkceService.randomVerifier());
        pkceService.saveChallenge("code-2", PkceService.METHOD_S256, challenge, 60);

        assertThatThrownBy(() -> pkceService.verifyAndConsume("code-2", pkceService.randomVerifier()))
                .isInstanceOf(SaOAuth2Exception.class)
                .hasMessageContaining("PKCE");
    }

    @Test
    void plainMethodIsRejected() {
        assertThatThrownBy(() -> pkceService.saveChallenge("code-3", "plain", "challenge-value", 60))
                .isInstanceOf(SaOAuth2Exception.class)
                .hasMessageContaining("S256");
    }

    @Test
    void challengeWithoutVerifierReportsMissingParam() {
        String challenge = pkceService.challengeOf(PkceService.METHOD_S256, pkceService.randomVerifier());
        pkceService.saveChallenge("code-4", PkceService.METHOD_S256, challenge, 60);

        assertThatThrownBy(() -> pkceService.verifyAndConsume("code-4", ""))
                .isInstanceOf(SaOAuth2Exception.class)
                .hasMessageContaining("code_verifier");
    }
}
