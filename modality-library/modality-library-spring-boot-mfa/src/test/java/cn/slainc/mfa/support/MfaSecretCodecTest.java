package cn.slainc.mfa.support;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.key.CryptoKeyProvider;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaSecretCodecTest {

    private final SecureRandom random = new SecureRandom();

    @Test
    void shouldEncryptAndDecryptWithTheCurrentKey() {
        CryptoKeyProvider provider = provider(Map.of("v1", aesKey()), "v1");
        MfaSecretCodec codec = new MfaSecretCodec(provider);

        String cipher = codec.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(cipher).isNotEqualTo("JBSWY3DPEHPK3PXP").startsWith("mfa1.");
        assertThat(codec.decrypt(cipher)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(codec.keyId()).isEqualTo("v1");
    }

    @Test
    void shouldDecryptWithTheKeyIdRecordedInTheCipherText() {
        Map<String, SecretKey> keys = new LinkedHashMap<>();
        keys.put("v1", aesKey());
        keys.put("v2", aesKey());
        MfaSecretCodec rotating = new MfaSecretCodec(provider(keys, "v2"));
        String cipher = rotating.encrypt("JBSWY3DPEHPK3PXP");

        MfaSecretCodec afterRotation = new MfaSecretCodec(provider(keys, "v1"));

        assertThat(afterRotation.decrypt(cipher)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void shouldFallBackToPlainTextWithoutAKeyProvider() {
        MfaSecretCodec codec = new MfaSecretCodec(null);

        assertThat(codec.encrypt("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(codec.decrypt("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(codec.keyId()).isNull();
        assertThat(codec.encrypt(null)).isNull();
        assertThat(codec.decrypt(null)).isNull();
    }

    @Test
    void shouldReturnNonPrefixedValuesUnchanged() {
        MfaSecretCodec codec = new MfaSecretCodec(provider(Map.of("v1", aesKey()), "v1"));

        assertThat(codec.decrypt("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(codec.decrypt("mfa1.only-two-parts")).isEqualTo("mfa1.only-two-parts");
    }

    @Test
    void shouldFailLoudlyForACorruptedCipherText() {
        MfaSecretCodec codec = new MfaSecretCodec(provider(Map.of("v1", aesKey()), "v1"));
        String cipher = codec.encrypt("JBSWY3DPEHPK3PXP");

        String corrupted = cipher.substring(0, cipher.length() - 4);

        assertThatThrownBy(() -> codec.decrypt(corrupted)).isInstanceOf(RuntimeException.class);
    }

    private SecretKey aesKey() {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        return new SecretKeySpec(raw, "AES");
    }

    private CryptoKeyProvider provider(Map<String, SecretKey> keys, String currentKeyId) {
        return new CryptoKeyProvider() {

            @Override
            public SecretKey key(String keyId) {
                SecretKey key = keys.get(keyId);
                if (key == null) {
                    throw new CryptoException("未知的密钥标识：" + keyId);
                }
                return key;
            }

            @Override
            public String currentKeyId() {
                return currentKeyId;
            }
        };
    }
}
