package cn.slainc.webhook.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

    private static final String SECRET = "whsec_test_secret";

    private static final long TIMESTAMP = 1700000000L;

    private static final String BODY = "{\"event\":\"open.apikey.created\",\"id\":1}";

    @Test
    void shouldSignTheTimestampAndBodyWithHmacSha256() {
        assertThat(WebhookSignature.sign(SECRET, TIMESTAMP, BODY))
                .isEqualTo("c260be16880636494238cba9a341f5b77431b7be053753c18f44bae1be3eb608");
    }

    @Test
    void shouldBuildTheSignatureHeader() {
        assertThat(WebhookSignature.header(SECRET, TIMESTAMP, BODY))
                .isEqualTo("t=1700000000,v1=c260be16880636494238cba9a341f5b77431b7be053753c18f44bae1be3eb608");
    }

    @Test
    void shouldNotSignWithoutASecret() {
        assertThat(WebhookSignature.sign(null, TIMESTAMP, BODY)).isNull();
        assertThat(WebhookSignature.header("  ", TIMESTAMP, BODY)).isNull();
    }

    @Test
    void shouldVerifyAHeaderInsideTheToleranceWindow() {
        String header = WebhookSignature.header(SECRET, java.time.Instant.now().getEpochSecond(), BODY);

        assertThat(WebhookSignature.verify(SECRET, header, BODY, 300L)).isTrue();
        assertThat(WebhookSignature.verify(SECRET, header, BODY + "x", 300L)).isFalse();
        assertThat(WebhookSignature.verify("other", header, BODY, 300L)).isFalse();
    }

    @Test
    void shouldRejectAStaleOrMalformedHeader() {
        String stale = WebhookSignature.header(SECRET, 1L, BODY);

        assertThat(WebhookSignature.verify(SECRET, stale, BODY, 300L)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "v1=abc", BODY, 300L)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "t=1700000000", BODY, 300L)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, null, BODY, 300L)).isFalse();
    }
}
