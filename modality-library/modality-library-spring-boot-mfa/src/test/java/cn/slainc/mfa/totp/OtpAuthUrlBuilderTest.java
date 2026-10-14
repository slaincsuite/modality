package cn.slainc.mfa.totp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpAuthUrlBuilderTest {

    private final OtpAuthUrlBuilder builder = new OtpAuthUrlBuilder();

    @Test
    void shouldBuildTheStandardOtpauthUrl() {
        assertThat(builder.build("modality", "alice", "JBSWY3DPEHPK3PXP"))
                .isEqualTo("otpauth://totp/modality:alice?secret=JBSWY3DPEHPK3PXP&issuer=modality"
                        + "&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    void shouldPercentEncodeTheIssuerAndKeepTheCommonCharacters() {
        assertThat(builder.build("Modality Cloud", "alice@example.com", "JBSWY3DPEHPK3PXP"))
                .isEqualTo("otpauth://totp/Modality%20Cloud:alice@example.com?secret=JBSWY3DPEHPK3PXP"
                        + "&issuer=Modality%20Cloud&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    void shouldHonourCustomDigitsAndPeriod() {
        assertThat(builder.build("modality", "alice", "JBSWY3DPEHPK3PXP", 8, 60))
                .isEqualTo("otpauth://totp/modality:alice?secret=JBSWY3DPEHPK3PXP&issuer=modality"
                        + "&algorithm=SHA1&digits=8&period=60");
    }

    @Test
    void shouldFallBackToTheDefaultIssuer() {
        assertThat(builder.build(null, null, "JBSWY3DPEHPK3PXP"))
                .isEqualTo("otpauth://totp/modality:?secret=JBSWY3DPEHPK3PXP&issuer=modality"
                        + "&algorithm=SHA1&digits=6&period=30");
        assertThat(builder.build("  ", "  ", "JBSWY3DPEHPK3PXP"))
                .isEqualTo("otpauth://totp/modality:?secret=JBSWY3DPEHPK3PXP&issuer=modality"
                        + "&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    void shouldEncodeUnicodeAccounts() {
        assertThat(builder.build("modality", "张三", "JBSWY3DPEHPK3PXP"))
                .isEqualTo("otpauth://totp/modality:%E5%BC%A0%E4%B8%89?secret=JBSWY3DPEHPK3PXP"
                        + "&issuer=modality&algorithm=SHA1&digits=6&period=30");
    }
}
