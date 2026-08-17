package cn.slainc.crypto.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.symmetric.AesKeySize;
import cn.slainc.crypto.symmetric.AesUtils;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class EnvelopeEncryptionTest {

    @Test
    void wrapsKeysWithAesKek() {
        SecretKey kek = AesUtils.generateKey(AesKeySize.AES_256);
        SecretKey dek = AesUtils.generateKey(AesKeySize.AES_256);
        byte[] wrapped = KeyWrapper.wrap(kek, dek.getEncoded());
        assertEquals(40, wrapped.length);
        assertArrayEquals(dek.getEncoded(), KeyWrapper.unwrap(kek, wrapped, "AES"));
        SecretKey other = AesUtils.generateKey(AesKeySize.AES_256);
        assertThrows(CryptoException.class, () -> KeyWrapper.unwrap(other, wrapped, "AES"));
    }

    @Test
    void wrapsKeysWithSm4Kek() {
        SecretKey kek = SecretKeyUtils.sm4Key();
        SecretKey dek = AesUtils.generateKey(AesKeySize.AES_128);
        byte[] wrapped = KeyWrapper.wrap(kek, dek.getEncoded());
        assertEquals(16 + 32, wrapped.length);
        assertArrayEquals(dek.getEncoded(), KeyWrapper.unwrap(kek, wrapped, "AES"));
        assertThrows(CryptoException.class, () -> KeyWrapper.unwrap(SecretKeyUtils.sm4Key(), wrapped, "AES"));
        assertThrows(CryptoException.class, () -> KeyWrapper.unwrapSm4(kek.getEncoded(), new byte[16]));
        assertThrows(CryptoException.class, () -> KeyWrapper.wrapAes(new byte[8], dek.getEncoded()));
    }

    @Test
    void encryptsAndDecryptsEnvelope() {
        SecretKey kek = AesUtils.generateKey(AesKeySize.AES_256);
        EnvelopeEncryption envelope = new EnvelopeEncryption(kek, "kek-2025");
        byte[] plainText = "modality 信封加密".getBytes(StandardCharsets.UTF_8);
        EncryptedData data = envelope.encrypt(plainText);
        assertEquals("kek-2025", data.keyId());
        assertEquals("AES", data.keyAlgorithm());
        assertEquals(12, data.iv().length);
        assertEquals(plainText.length + 16, data.cipherText().length);
        assertArrayEquals(plainText, envelope.decrypt(data));
        assertEquals(40, data.wrappedKey().length);
        assertEquals(kek, envelope.keyEncryptionKey());
        assertEquals("kek-2025", envelope.keyId());
    }

    @Test
    void failsWithWrongOrTamperedKey() {
        EnvelopeEncryption envelope = new EnvelopeEncryption(AesUtils.generateKey(AesKeySize.AES_256));
        EncryptedData data = envelope.encrypt("secret".getBytes(StandardCharsets.UTF_8));
        EnvelopeEncryption other = new EnvelopeEncryption(AesUtils.generateKey(AesKeySize.AES_256));
        assertThrows(CryptoException.class, () -> other.decrypt(data));
        byte[] tampered = data.cipherText();
        tampered[0] ^= 0x01;
        EncryptedData modified = new EncryptedData(data.keyId(), data.keyAlgorithm(), data.wrappedKey(), data.iv(), tampered);
        assertThrows(CryptoException.class, () -> envelope.decrypt(modified));
        assertEquals("secret".getBytes(StandardCharsets.UTF_8).length + 16, data.cipherText().length);
    }
}
