package cn.slainc.oauth2.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientSecretCipherTest {

    @Test
    void passphraseDerivedKeyRoundTrips() {
        ClientSecretCipher cipher = new ClientSecretCipher("modality-oauth2-passphrase");
        String encrypted = cipher.encrypt("s3cret-value");
        assertThat(encrypted).startsWith(ClientSecretCipher.PREFIX).endsWith(ClientSecretCipher.SUFFIX);
        assertThat(cipher.decrypt(encrypted)).isEqualTo("s3cret-value");
    }

    @Test
    void samePassphraseDerivesStableKey() {
        String encrypted = new ClientSecretCipher("shared-passphrase").encrypt("s3cret-value");
        assertThat(new ClientSecretCipher("shared-passphrase").decrypt(encrypted)).isEqualTo("s3cret-value");
    }

    @Test
    void differentPassphraseCannotDecrypt() {
        String encrypted = new ClientSecretCipher("passphrase-a").encrypt("s3cret-value");
        assertThatThrownBy(() -> new ClientSecretCipher("passphrase-b").decrypt(encrypted))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void hexKeyIsStillAccepted() {
        ClientSecretCipher cipher = new ClientSecretCipher("hex:00112233445566778899aabbccddeeff");
        assertThat(cipher.isEnabled()).isTrue();
        assertThat(cipher.decrypt(cipher.encrypt("s3cret-value"))).isEqualTo("s3cret-value");
    }

    @Test
    void disabledCipherLeavesPlainSecretUntouched() {
        ClientSecretCipher cipher = new ClientSecretCipher(" ");
        assertThat(cipher.encrypt("s3cret-value")).isEqualTo("s3cret-value");
        assertThat(cipher.decrypt("s3cret-value")).isEqualTo("s3cret-value");
    }
}
