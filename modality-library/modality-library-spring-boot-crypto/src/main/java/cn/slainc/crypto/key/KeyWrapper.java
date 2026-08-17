package cn.slainc.crypto.key;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.sm.Sm4Utils;
import cn.slainc.crypto.symmetric.CipherMode;
import cn.slainc.crypto.symmetric.Padding;
import java.security.GeneralSecurityException;
import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class KeyWrapper {

    public static final String AES_ALGORITHM = "AES";

    public static final String SM4_ALGORITHM = "SM4";

    private KeyWrapper() {
    }

    public static byte[] wrap(SecretKey kek, byte[] dek) {
        return SM4_ALGORITHM.equalsIgnoreCase(kek.getAlgorithm()) ? wrapSm4(kek.getEncoded(), dek) : wrapAes(kek.getEncoded(), dek);
    }

    public static byte[] unwrap(SecretKey kek, byte[] wrapped, String algorithm) {
        return SM4_ALGORITHM.equalsIgnoreCase(kek.getAlgorithm()) ? unwrapSm4(kek.getEncoded(), wrapped) : unwrapAes(kek.getEncoded(), wrapped, algorithm);
    }

    public static byte[] wrapAes(byte[] kek, byte[] dek) {
        if (kek == null || kek.length != 16 && kek.length != 24 && kek.length != 32) {
            throw new CryptoException("密钥加密密钥长度必须为 16、24 或 32 字节");
        }
        try {
            Cipher cipher = Cipher.getInstance("AESWrap", CryptoProviders.bouncyCastle());
            cipher.init(Cipher.WRAP_MODE, new SecretKeySpec(kek, AES_ALGORITHM));
            return cipher.wrap(new SecretKeySpec(dek, AES_ALGORITHM));
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法包装数据密钥", exception);
        }
    }

    public static byte[] unwrapAes(byte[] kek, byte[] wrapped, String algorithm) {
        try {
            Cipher cipher = Cipher.getInstance("AESWrap", CryptoProviders.bouncyCastle());
            cipher.init(Cipher.UNWRAP_MODE, new SecretKeySpec(kek, AES_ALGORITHM));
            Key key = cipher.unwrap(wrapped, algorithm, Cipher.SECRET_KEY);
            return key.getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法解包数据密钥", exception);
        }
    }

    public static byte[] wrapSm4(byte[] kek, byte[] dek) {
        byte[] iv = SecureRandomUtils.nextBytes(Sm4Utils.BLOCK_LENGTH);
        byte[] cipherText = Sm4Utils.encrypt(CipherMode.CBC, Padding.PKCS7, kek, iv, dek);
        byte[] output = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, output, 0, iv.length);
        System.arraycopy(cipherText, 0, output, iv.length, cipherText.length);
        return output;
    }

    public static byte[] unwrapSm4(byte[] kek, byte[] wrapped) {
        if (wrapped == null || wrapped.length <= Sm4Utils.BLOCK_LENGTH) {
            throw new CryptoException("被包装的密钥长度不足");
        }
        byte[] iv = new byte[Sm4Utils.BLOCK_LENGTH];
        System.arraycopy(wrapped, 0, iv, 0, iv.length);
        byte[] cipherText = new byte[wrapped.length - iv.length];
        System.arraycopy(wrapped, iv.length, cipherText, 0, cipherText.length);
        return Sm4Utils.decrypt(CipherMode.CBC, Padding.PKCS7, kek, iv, cipherText);
    }
}
