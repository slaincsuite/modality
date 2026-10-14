package cn.slainc.mfa.totp;

import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.mfa.config.MfaProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpEngineTest {

    // RFC 6238 附录 B 的 SHA1 测试密钥：ASCII "12345678901234567890" 的 Base32 形式
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final MfaProperties properties = new MfaProperties();

    private final TotpEngine totp = new TotpEngine(new MfaConfigReader(properties, null));

    @Test
    void shouldGenerateAVerifiableSecret() {
        String secret = totp.generateSecretKey();

        assertThat(secret).isNotBlank();
        assertThat(secret).hasSize(32).doesNotContain("=");
        assertThat(totp.verify(secret, totp.generate(secret))).isTrue();
    }

    @Test
    void shouldMatchTheRfc6238Sha1TestVectors() {
        properties.setDigits(8);
        TotpEngine engine = new TotpEngine(new MfaConfigReader(properties, null));

        assertThat(engine.generate(RFC_SECRET, 59L)).isEqualTo("94287082");
        assertThat(engine.generate(RFC_SECRET, 1111111109L)).isEqualTo("07081804");
        assertThat(engine.generate(RFC_SECRET, 1234567890L)).isEqualTo("89005924");
        assertThat(engine.generate(RFC_SECRET, 2000000000L)).isEqualTo("69279037");
        assertThat(engine.generate(RFC_SECRET, 20000000000L)).isEqualTo("65353130");
    }

    @Test
    void shouldVerifyWithTheTimeWindow() {
        properties.setDigits(8);
        TotpEngine engine = new TotpEngine(new MfaConfigReader(properties, null));

        assertThat(engine.verify(RFC_SECRET, "94287082", 0, 59L)).isTrue();
        assertThat(engine.verify(RFC_SECRET, "94287082", 0, 29L)).isFalse();
        assertThat(engine.verify(RFC_SECRET, "94287082", 1, 29L)).isTrue();
        assertThat(engine.verify(RFC_SECRET, "94287082", 1, 89L)).isTrue();
    }

    @Test
    void shouldAcceptSecretsWithSpacesPaddingAndLowercase() {
        properties.setDigits(8);
        TotpEngine engine = new TotpEngine(new MfaConfigReader(properties, null));
        String spaced = " gezd gnbv gy3t qojq gezd gnbv gy3t qojq ";

        assertThat(engine.verify(spaced, engine.generate(RFC_SECRET, 59L), 0, 59L)).isTrue();
    }

    @Test
    void shouldRejectWrongBlankOrMalformedCodes() {
        String secret = totp.generateSecretKey();
        String valid = totp.generate(secret);

        assertThat(totp.verify(secret, wrong(valid))).isFalse();
        assertThat(totp.verify(secret, null)).isFalse();
        assertThat(totp.verify(secret, "  ")).isFalse();
        assertThat(totp.verify(secret, "abcdef")).isFalse();
        assertThat(totp.verify(secret, "123")).isFalse();
        assertThat(totp.verify(null, valid)).isFalse();
        assertThat(totp.verify("not-base32!!", valid)).isFalse();
    }

    @Test
    void shouldFollowTheConfiguredDigitsAndPeriod() {
        properties.setDigits(8);
        properties.setPeriodSeconds(60);

        TotpEngine custom = new TotpEngine(new MfaConfigReader(properties, null));

        assertThat(custom.digits()).isEqualTo(8);
        assertThat(custom.periodSeconds()).isEqualTo(60);
        String secret = custom.generateSecretKey();
        assertThat(custom.generate(secret)).hasSize(8);
        assertThat(custom.verify(secret, custom.generate(secret))).isTrue();
    }

    @Test
    void shouldClampOutOfRangeConfiguration() {
        properties.setDigits(99);
        properties.setPeriodSeconds(0);
        properties.setWindow(-3);

        TotpEngine clamped = new TotpEngine(new MfaConfigReader(properties, null));

        assertThat(clamped.digits()).isEqualTo(8);
        assertThat(clamped.periodSeconds()).isEqualTo(30);
        assertThat(clamped.window()).isZero();
    }

    @Test
    void shouldBuildTheOtpAuthUriWithTheConfiguredParameters() {
        String uri = totp.otpAuthUri("modality", "alice", RFC_SECRET);

        assertThat(uri).startsWith("otpauth://totp/modality:alice?secret=" + RFC_SECRET + "&issuer=modality");
        assertThat(uri).contains("&algorithm=SHA1").contains("&digits=6").contains("&period=30");
    }

    private static String wrong(String code) {
        char last = code.charAt(code.length() - 1);
        return code.substring(0, code.length() - 1) + (last == '0' ? '1' : '0');
    }
}
