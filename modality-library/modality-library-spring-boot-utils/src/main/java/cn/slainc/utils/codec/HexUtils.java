package cn.slainc.utils.codec;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class HexUtils {

    private static final HexFormat LOWER = HexFormat.of();
    private static final HexFormat UPPER = HexFormat.of().withUpperCase();

    private HexUtils() {
    }

    public static String encode(byte[] bytes) {
        return bytes == null ? null : LOWER.formatHex(bytes);
    }

    public static String encodeUpper(byte[] bytes) {
        return bytes == null ? null : UPPER.formatHex(bytes);
    }

    public static String encode(String text) {
        return text == null ? null : encode(text.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] decode(String hex) {
        if (hex == null) {
            return null;
        }
        String normalized = hex.trim();
        if (normalized.isEmpty()) {
            return new byte[0];
        }
        if (normalized.length() % 2 != 0) {
            throw new IllegalArgumentException("十六进制字符串长度必须为偶数");
        }
        return LOWER.parseHex(normalized);
    }

    public static String decodeToString(String hex) {
        byte[] bytes = decode(hex);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public static boolean isHex(String text) {
        if (text == null || text.isEmpty() || text.length() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
