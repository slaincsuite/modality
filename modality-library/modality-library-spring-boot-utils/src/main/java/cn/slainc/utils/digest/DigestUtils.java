package cn.slainc.utils.digest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DigestUtils {

    private static final int BUFFER_SIZE = 8192;

    private DigestUtils() {
    }

    public static String md5(String text) {
        return digestToString("MD5", text == null ? null : text.getBytes(StandardCharsets.UTF_8));
    }

    public static String md5(byte[] bytes) {
        return digestToString("MD5", bytes);
    }

    public static String sha1(String text) {
        return digestToString("SHA-1", text == null ? null : text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha1(byte[] bytes) {
        return digestToString("SHA-1", bytes);
    }

    public static String sha256(String text) {
        return digestToString("SHA-256", text == null ? null : text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        return digestToString("SHA-256", bytes);
    }

    public static String sha512(String text) {
        return digestToString("SHA-512", text == null ? null : text.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] digest(String algorithm, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return MessageDigest.getInstance(algorithm).digest(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("不支持的摘要算法: " + algorithm, ex);
        }
    }

    public static String digestToString(String algorithm, byte[] bytes) {
        return cn.slainc.utils.codec.HexUtils.encode(digest(algorithm, bytes));
    }

    public static String digestStream(String algorithm, InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return cn.slainc.utils.codec.HexUtils.encode(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("不支持的摘要算法: " + algorithm, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("流式摘要计算失败", ex);
        }
    }

    public static String md5Stream(InputStream inputStream) {
        return digestStream("MD5", inputStream);
    }

    public static String sha256Stream(InputStream inputStream) {
        return digestStream("SHA-256", inputStream);
    }

    public static boolean constantTimeEquals(byte[] left, byte[] right) {
        return MessageDigest.isEqual(left, right);
    }
}
