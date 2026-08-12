package cn.slainc.utils.codec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base64Utils {

    private Base64Utils() {
    }

    public static String encode(String text) {
        return text == null ? null : encode(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String encode(byte[] bytes) {
        return bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] decode(String encoded) {
        return encoded == null ? null : Base64.getDecoder().decode(encoded);
    }

    public static String decodeToString(String encoded) {
        byte[] bytes = decode(encoded);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public static String encodeUrlSafe(String text) {
        return text == null ? null : encodeUrlSafe(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String encodeUrlSafe(byte[] bytes) {
        return bytes == null ? null : Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] decodeUrlSafe(String encoded) {
        return encoded == null ? null : Base64.getUrlDecoder().decode(encoded);
    }

    public static String decodeUrlSafeToString(String encoded) {
        byte[] bytes = decodeUrlSafe(encoded);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public static boolean isBase64(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            Base64.getDecoder().decode(text);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
