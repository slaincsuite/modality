package cn.slainc.crypto.key;

import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.symmetric.AesKeySize;
import cn.slainc.crypto.symmetric.AesUtils;
import cn.slainc.crypto.symmetric.CipherMode;
import cn.slainc.crypto.symmetric.Padding;
import javax.crypto.SecretKey;

public final class EnvelopeEncryption {

    public static final int IV_LENGTH = 12;

    private final SecretKey keyEncryptionKey;

    private final String keyId;

    public EnvelopeEncryption(SecretKey keyEncryptionKey) {
        this(keyEncryptionKey, KeyRing.DEFAULT_KEY_ID);
    }

    public EnvelopeEncryption(SecretKey keyEncryptionKey, String keyId) {
        this.keyEncryptionKey = keyEncryptionKey;
        this.keyId = keyId;
    }

    public EncryptedData encrypt(byte[] plainText) {
        SecretKey dataKey = AesUtils.generateKey(AesKeySize.AES_256);
        byte[] iv = SecureRandomUtils.nextBytes(IV_LENGTH);
        byte[] cipherText = AesUtils.encrypt(CipherMode.GCM, Padding.NONE, dataKey.getEncoded(), iv, plainText);
        byte[] wrappedKey = KeyWrapper.wrap(keyEncryptionKey, dataKey.getEncoded());
        return new EncryptedData(keyId, dataKey.getAlgorithm(), wrappedKey, iv, cipherText);
    }

    public byte[] decrypt(EncryptedData data) {
        byte[] dataKey = KeyWrapper.unwrap(keyEncryptionKey, data.wrappedKey(), data.keyAlgorithm());
        return AesUtils.decrypt(CipherMode.GCM, Padding.NONE, dataKey, data.iv(), data.cipherText());
    }

    public SecretKey keyEncryptionKey() {
        return keyEncryptionKey;
    }

    public String keyId() {
        return keyId;
    }
}
