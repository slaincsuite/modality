package cn.slainc.crypto.key;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.symmetric.AesKeySize;
import cn.slainc.crypto.symmetric.AesUtils;
import cn.slainc.crypto.symmetric.DesUtils;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class SecretKeyUtils {

    public static final String HEX_PREFIX = "hex:";

    public static final String BASE64_PREFIX = "base64:";

    private SecretKeyUtils() {
    }

    public static SecretKey aesKey(AesKeySize keySize) {
        return AesUtils.generateKey(keySize);
    }

    public static SecretKey aesKey(byte[] bytes) {
        AesUtils.requireKey(bytes);
        return new SecretKeySpec(bytes, "AES");
    }

    public static SecretKey sm4Key() {
        return new SecretKeySpec(randomKey(16), "SM4");
    }

    public static SecretKey sm4Key(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new CryptoException("SM4 密钥长度必须为 16 字节");
        }
        return new SecretKeySpec(bytes, "SM4");
    }

    // DES 系入口与 DesUtils 同生命周期：弱算法仅保留显式开启的过渡能力，随 DesUtils 一并移除
    @Deprecated(forRemoval = true)
    public static SecretKey desKey() {
        return DesUtils.generateKey();
    }

    @Deprecated(forRemoval = true)
    public static SecretKey desKey(byte[] bytes) {
        return DesUtils.key(bytes);
    }

    @Deprecated(forRemoval = true)
    public static SecretKey tripleDesKey() {
        return DesUtils.generateTripleDesKey();
    }

    @Deprecated(forRemoval = true)
    public static SecretKey tripleDesKey(byte[] bytes) {
        return DesUtils.tripleKey(bytes);
    }

    public static SecretKey hmacKey(String algorithm, int length) {
        return new SecretKeySpec(randomKey(length), algorithm);
    }

    public static byte[] randomKey(int length) {
        return SecureRandomUtils.nextBytes(length);
    }

    public static String encodeBase64(SecretKey key) {
        return Base64Utils.encode(key.getEncoded());
    }

    public static String encodeHex(SecretKey key) {
        return HexUtils.encode(key.getEncoded());
    }

    public static SecretKey decodeBase64(String text, String algorithm) {
        return new SecretKeySpec(Base64Utils.decode(text), algorithm);
    }

    public static SecretKey decodeHex(String text, String algorithm) {
        return new SecretKeySpec(HexUtils.decode(text), algorithm);
    }

    public static SecretKey decode(String text, String algorithm) {
        if (text == null) {
            throw new CryptoException("密钥文本不能为空");
        }
        String value = text.trim();
        if (value.startsWith(HEX_PREFIX)) {
            return decodeHex(value.substring(HEX_PREFIX.length()), algorithm);
        }
        if (value.startsWith(BASE64_PREFIX)) {
            return decodeBase64(value.substring(BASE64_PREFIX.length()), algorithm);
        }
        return decodeBase64(value, algorithm);
    }

    public static SecretKey fromPassword(String password, String algorithm) {
        return new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), algorithm);
    }
}
