package cn.slainc.crypto.symmetric;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.security.GeneralSecurityException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.engines.DESEngine;
import org.bouncycastle.crypto.engines.DESedeEngine;

@Deprecated(forRemoval = true)
public final class DesUtils {

    public static final int BLOCK_LENGTH = 8;

    public static final int KEY_LENGTH = 8;

    public static final int TRIPLE_KEY_LENGTH = 24;

    private DesUtils() {
    }

    public static SecretKey generateKey() {
        WeakAlgorithmPolicy.requireOptIn("DES/DESede");
        try {
            KeyGenerator generator = KeyGenerator.getInstance("DES");
            generator.init(56, SecureRandomUtils.secureRandom());
            return generator.generateKey();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法生成 DES 密钥", exception);
        }
    }

    public static SecretKey generateTripleDesKey() {
        WeakAlgorithmPolicy.requireOptIn("DES/DESede");
        try {
            KeyGenerator generator = KeyGenerator.getInstance("DESede");
            generator.init(168, SecureRandomUtils.secureRandom());
            return generator.generateKey();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法生成 DESede 密钥", exception);
        }
    }

    public static SecretKey key(byte[] bytes) {
        WeakAlgorithmPolicy.requireOptIn("DES/DESede");
        requireKey(bytes, KEY_LENGTH, "DES");
        return new SecretKeySpec(bytes, "DES");
    }

    public static SecretKey tripleKey(byte[] bytes) {
        WeakAlgorithmPolicy.requireOptIn("DES/DESede");
        requireTripleKey(bytes);
        return new SecretKeySpec(bytes, "DESede");
    }

    public static byte[] encryptDes(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        WeakAlgorithmPolicy.requireOptIn("DES/DESede");
        requireKey(key, KEY_LENGTH, "DES");
        return BlockCipherUtils.encrypt(new DESEngine(), mode, padding, key, iv, data);
    }

    public static byte[] decryptDes(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        WeakAlgorithmPolicy.requireOptIn("DES/DESede");
        requireKey(key, KEY_LENGTH, "DES");
        return BlockCipherUtils.decrypt(new DESEngine(), mode, padding, key, iv, data);
    }

    public static byte[] encryptTripleDes(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        WeakAlgorithmPolicy.requireOptIn("DES/DESede");
        requireTripleKey(key);
        return BlockCipherUtils.encrypt(new DESedeEngine(), mode, padding, key, iv, data);
    }

    public static byte[] decryptTripleDes(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        WeakAlgorithmPolicy.requireOptIn("DES/DESede");
        requireTripleKey(key);
        return BlockCipherUtils.decrypt(new DESedeEngine(), mode, padding, key, iv, data);
    }

    private static void requireTripleKey(byte[] key) {
        if (key == null || key.length != TRIPLE_KEY_LENGTH && key.length != 16) {
            throw new CryptoException("DESede 密钥长度必须为 16 或 24 字节");
        }
    }

    private static void requireKey(byte[] key, int length, String algorithm) {
        if (key == null || key.length != length) {
            throw new CryptoException(algorithm + " 密钥长度必须为 " + length + " 字节");
        }
    }
}