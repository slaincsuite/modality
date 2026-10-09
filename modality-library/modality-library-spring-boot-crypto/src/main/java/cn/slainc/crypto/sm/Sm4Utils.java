package cn.slainc.crypto.sm;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.symmetric.BlockCipherUtils;
import cn.slainc.crypto.symmetric.CipherMode;
import cn.slainc.crypto.symmetric.Padding;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.engines.SM4Engine;

public final class Sm4Utils {

    public static final int KEY_LENGTH = 16;

    public static final int BLOCK_LENGTH = 16;

    public static final int GCM_IV_LENGTH = 12;

    private Sm4Utils() {
    }

    public static SecretKey generateKey() {
        return key(SecureRandomUtils.nextBytes(KEY_LENGTH));
    }

    public static SecretKey key(byte[] bytes) {
        requireKey(bytes);
        return new SecretKeySpec(bytes, "SM4");
    }

    public static byte[] encrypt(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        requireKey(key);
        return BlockCipherUtils.encrypt(new SM4Engine(), mode, padding, key, iv, data);
    }

    public static byte[] decrypt(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        requireKey(key);
        return BlockCipherUtils.decrypt(new SM4Engine(), mode, padding, key, iv, data);
    }

    public static byte[] encrypt(CipherMode mode, Padding padding, SecretKey key, byte[] iv, byte[] data) {
        return encrypt(mode, padding, key.getEncoded(), iv, data);
    }

    public static byte[] decrypt(CipherMode mode, Padding padding, SecretKey key, byte[] iv, byte[] data) {
        return decrypt(mode, padding, key.getEncoded(), iv, data);
    }

    public static byte[] encryptCbc(byte[] key, byte[] iv, byte[] data) {
        return encrypt(CipherMode.CBC, Padding.PKCS7, key, iv, data);
    }

    public static byte[] decryptCbc(byte[] key, byte[] iv, byte[] data) {
        return decrypt(CipherMode.CBC, Padding.PKCS7, key, iv, data);
    }

    public static byte[] encryptCtr(byte[] key, byte[] iv, byte[] data) {
        return encrypt(CipherMode.CTR, Padding.NONE, key, iv, data);
    }

    public static byte[] decryptCtr(byte[] key, byte[] iv, byte[] data) {
        return decrypt(CipherMode.CTR, Padding.NONE, key, iv, data);
    }

    public static byte[] encryptGcm(byte[] key, byte[] iv, byte[] data) {
        return encrypt(CipherMode.GCM, Padding.NONE, key, iv, data);
    }

    public static byte[] decryptGcm(byte[] key, byte[] iv, byte[] data) {
        return decrypt(CipherMode.GCM, Padding.NONE, key, iv, data);
    }

    public static byte[] encryptGcm(byte[] key, byte[] data) {
        byte[] iv = SecureRandomUtils.nextBytes(GCM_IV_LENGTH);
        byte[] cipherText = encryptGcm(key, iv, data);
        byte[] output = Arrays.copyOf(iv, iv.length + cipherText.length);
        System.arraycopy(cipherText, 0, output, iv.length, cipherText.length);
        return output;
    }

    public static byte[] decryptGcm(byte[] key, byte[] cipherText) {
        if (cipherText == null || cipherText.length < GCM_IV_LENGTH + BLOCK_LENGTH) {
            throw new CryptoException("SM4 GCM 数据长度不足");
        }
        byte[] iv = Arrays.copyOf(cipherText, GCM_IV_LENGTH);
        byte[] data = Arrays.copyOfRange(cipherText, GCM_IV_LENGTH, cipherText.length);
        return decryptGcm(key, iv, data);
    }

    public static String encryptBase64(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        return Base64Utils.encode(encrypt(mode, padding, key, iv, data));
    }

    public static byte[] decryptBase64(CipherMode mode, Padding padding, byte[] key, byte[] iv, String data) {
        return decrypt(mode, padding, key, iv, Base64Utils.decode(data));
    }

    public static String encryptHex(CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        return HexUtils.encode(encrypt(mode, padding, key, iv, data));
    }

    public static byte[] decryptHex(CipherMode mode, Padding padding, byte[] key, byte[] iv, String data) {
        return decrypt(mode, padding, key, iv, HexUtils.decode(data));
    }

    private static void requireKey(byte[] key) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new CryptoException("SM4 密钥长度必须为 " + KEY_LENGTH + " 字节");
        }
    }
}
