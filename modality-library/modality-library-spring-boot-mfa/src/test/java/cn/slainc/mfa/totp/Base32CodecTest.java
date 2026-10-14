package cn.slainc.mfa.totp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base32CodecTest {

    @Test
    void shouldEncodeTheRfc4648Vectors() {
        assertThat(Base32Codec.encode("".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(Base32Codec.encode("f".getBytes(StandardCharsets.UTF_8))).isEqualTo("MY");
        assertThat(Base32Codec.encode("fo".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXQ");
        assertThat(Base32Codec.encode("foo".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6");
        assertThat(Base32Codec.encode("foob".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6YQ");
        assertThat(Base32Codec.encode("fooba".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6YTB");
        assertThat(Base32Codec.encode("foobar".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6YTBOI");
    }

    @Test
    void shouldDecodeTheRfc4648Vectors() {
        assertThat(new String(Base32Codec.decode(""), StandardCharsets.UTF_8)).isEmpty();
        assertThat(new String(Base32Codec.decode("MY"), StandardCharsets.UTF_8)).isEqualTo("f");
        assertThat(new String(Base32Codec.decode("MZXQ"), StandardCharsets.UTF_8)).isEqualTo("fo");
        assertThat(new String(Base32Codec.decode("MZXW6"), StandardCharsets.UTF_8)).isEqualTo("foo");
        assertThat(new String(Base32Codec.decode("MZXW6YQ"), StandardCharsets.UTF_8)).isEqualTo("foob");
        assertThat(new String(Base32Codec.decode("MZXW6YTB"), StandardCharsets.UTF_8)).isEqualTo("fooba");
        assertThat(new String(Base32Codec.decode("MZXW6YTBOI======"), StandardCharsets.UTF_8)).isEqualTo("foobar");
    }

    @Test
    void shouldDecodeIgnoringPaddingCaseAndSeparators() {
        assertThat(new String(Base32Codec.decode("mzxw6ytboi"), StandardCharsets.UTF_8)).isEqualTo("foobar");
        assertThat(new String(Base32Codec.decode("MZ XW-6YQ"), StandardCharsets.UTF_8)).isEqualTo("foob");
        assertThat(new String(Base32Codec.decode("my======"), StandardCharsets.UTF_8)).isEqualTo("f");
    }

    @Test
    void shouldRejectIllegalCharacters() {
        assertThatThrownBy(() -> Base32Codec.decode("M1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base32Codec.decode("AB!CD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base32Codec.decode("中文")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRoundTripRandomBytes() {
        SecureRandom random = new SecureRandom();
        for (int length = 1; length <= 64; length++) {
            byte[] data = new byte[length];
            random.nextBytes(data);
            assertThat(Base32Codec.decode(Base32Codec.encode(data))).isEqualTo(data);
        }
    }

    @Test
    void shouldEncodeATwentyByteSecretAsThirtyTwoCharacters() {
        byte[] secret = new byte[20];
        assertThat(Base32Codec.encode(secret)).hasSize(32).matches("[A-Z2-7]+");
    }
}
