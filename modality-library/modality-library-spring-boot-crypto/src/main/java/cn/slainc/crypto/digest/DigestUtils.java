package cn.slainc.crypto.digest;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.HexUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DigestUtils {

    private DigestUtils() {
    }

    public static byte[] digest(DigestAlgorithm algorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(algorithm.jcaName()).digest(data);
        } catch (NoSuchAlgorithmException exception) {
            throw new CryptoException("不支持的摘要算法：" + algorithm, exception);
        }
    }

    public static byte[] digest(DigestAlgorithm algorithm, String text) {
        return digest(algorithm, text.getBytes(StandardCharsets.UTF_8));
    }

    public static String digestHex(DigestAlgorithm algorithm, byte[] data) {
        return HexUtils.encode(digest(algorithm, data));
    }

    public static String digestHex(DigestAlgorithm algorithm, String text) {
        return HexUtils.encode(digest(algorithm, text));
    }

    public static byte[] md5(byte[] data) {
        return digest(DigestAlgorithm.MD5, data);
    }

    public static String md5Hex(byte[] data) {
        return digestHex(DigestAlgorithm.MD5, data);
    }

    public static String md5Hex(String text) {
        return digestHex(DigestAlgorithm.MD5, text);
    }

    public static String sha1Hex(byte[] data) {
        return digestHex(DigestAlgorithm.SHA1, data);
    }

    public static String sha1Hex(String text) {
        return digestHex(DigestAlgorithm.SHA1, text);
    }

    public static byte[] sha256(byte[] data) {
        return digest(DigestAlgorithm.SHA256, data);
    }

    public static String sha256Hex(byte[] data) {
        return digestHex(DigestAlgorithm.SHA256, data);
    }

    public static String sha256Hex(String text) {
        return digestHex(DigestAlgorithm.SHA256, text);
    }

    public static String sha512Hex(String text) {
        return digestHex(DigestAlgorithm.SHA512, text);
    }
}
