package cn.slainc.crypto.random;

import java.security.SecureRandom;
import java.util.UUID;

public final class SecureRandomUtils {

    private static final char[] ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private static final class Holder {

        private static final SecureRandom RANDOM = new SecureRandom();
    }

    private SecureRandomUtils() {
    }

    public static SecureRandom secureRandom() {
        return Holder.RANDOM;
    }

    public static byte[] nextBytes(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("长度不能为负数");
        }
        byte[] bytes = new byte[length];
        Holder.RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("上界必须为正数");
        }
        return Holder.RANDOM.nextInt(bound);
    }

    public static int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("起始值必须小于上界");
        }
        return origin + Holder.RANDOM.nextInt(bound - origin);
    }

    public static long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("上界必须为正数");
        }
        long bits;
        long value;
        do {
            bits = Holder.RANDOM.nextLong() >>> 1;
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0L);
        return value;
    }

    public static String nextHex(int byteLength) {
        byte[] bytes = nextBytes(byteLength);
        char[] digits = "0123456789abcdef".toCharArray();
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = digits[value >>> 4];
            chars[i * 2 + 1] = digits[value & 0x0F];
        }
        return new String(chars);
    }

    public static String nextString(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("长度不能为负数");
        }
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = ALPHANUMERIC[Holder.RANDOM.nextInt(ALPHANUMERIC.length)];
        }
        return new String(chars);
    }

    public static String nextNumeric(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("长度不能为负数");
        }
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = (char) ('0' + Holder.RANDOM.nextInt(10));
        }
        return new String(chars);
    }

    public static String nextUuid() {
        return new UUID(Holder.RANDOM.nextLong(), Holder.RANDOM.nextLong()).toString();
    }
}
