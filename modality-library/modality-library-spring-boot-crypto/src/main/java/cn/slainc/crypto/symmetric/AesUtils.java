package cn.slainc.crypto.symmetric;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.engines.AESEngine;

public final class AesUtils {

    private AesUtils() {
    }

    public static SecretKey generateKey(AesKeySize keySize) {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(keySize.bits(), SecureRandomUtils.secureRandom());
            return generator.generateKey();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法生成 AES 密钥", exception);
        }
    }

    public static SecretKey generateKey() {
        return generateKey(AesKeySize.AES_256);
    }

    public static SecretKey key(byte[] bytes) {
        requireKey(bytes);
        return new SecretKeySpec(bytes, "AES");
    }

    public static byte[] encrypt(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        requireKey(key);
        return BlockCipherUtils.encrypt(AESEngine.newInstance(), mode, padding, key, iv, data);
    }

    public static byte[] decrypt(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        requireKey(key);
        return BlockCipherUtils.decrypt(AESEngine.newInstance(), mode, padding, key, iv, data);
    }

    public static byte[] encrypt(CipherMode mode, Padding padding, SecretKey key, byte[] iv, byte[] data) {
        return encrypt(mode, padding, key.getEncoded(), iv, data);
    }

    public static byte[] decrypt(CipherMode mode, Padding padding, SecretKey key, byte[] iv, byte[] data) {
        return decrypt(mode, padding, key.getEncoded(), iv, data);
    }

    public static byte[] encryptGcm(byte[] key, byte[] data) {
        byte[] iv = SecureRandomUtils.nextBytes(12);
        byte[] cipherText = encrypt(CipherMode.GCM, Padding.NONE, key, iv, data);
        byte[] output = Arrays.copyOf(iv, iv.length + cipherText.length);
        System.arraycopy(cipherText, 0, output, iv.length, cipherText.length);
        return output;
    }

    public static byte[] decryptGcm(byte[] key, byte[] cipherText) {
        if (cipherText == null || cipherText.length < 13) {
            throw new CryptoException("GCM 数据长度不足");
        }
        byte[] iv = Arrays.copyOf(cipherText, 12);
        byte[] data = Arrays.copyOfRange(cipherText, 12, cipherText.length);
        return decrypt(CipherMode.GCM, Padding.NONE, key, iv, data);
    }

    public static void requireKey(byte[] key) {
        if (key == null || key.length != 16 && key.length != 24 && key.length != 32) {
            throw new CryptoException("AES 密钥长度必须为 16、24 或 32 字节");
        }
    }
}
