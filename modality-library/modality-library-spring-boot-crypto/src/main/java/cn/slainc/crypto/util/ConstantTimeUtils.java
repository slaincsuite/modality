package cn.slainc.crypto.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ConstantTimeUtils {

    private ConstantTimeUtils() {
    }

    public static boolean equals(byte[] left, byte[] right) {
        if (left == null || right == null) {
            return left == right;
        }
        return MessageDigest.isEqual(left, right);
    }

    public static boolean equals(char[] left, char[] right) {
        if (left == null || right == null) {
            return left == right;
        }
        return MessageDigest.isEqual(toBytes(left), toBytes(right));
    }

    public static boolean equals(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return equals(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] toBytes(char[] chars) {
        byte[] bytes = new byte[chars.length * 2];
        for (int i = 0; i < chars.length; i++) {
            bytes[i * 2] = (byte) (chars[i] >>> 8);
            bytes[i * 2 + 1] = (byte) chars[i];
        }
        return bytes;
    }
}
