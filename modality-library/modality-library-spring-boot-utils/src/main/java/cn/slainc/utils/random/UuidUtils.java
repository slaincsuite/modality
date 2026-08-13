package cn.slainc.utils.random;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private UuidUtils() {
    }

    public static UUID uuid() {
        return UUID.randomUUID();
    }

    public static String uuidString() {
        return UUID.randomUUID().toString();
    }

    public static String uuidCompact() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static UUID uuidV7() {
        long timestamp = System.currentTimeMillis() & 0xFFFFFFFFFFFFL;
        long randomA = SECURE_RANDOM.nextLong();
        long randomB = SECURE_RANDOM.nextLong();
        long mostSigBits = (timestamp << 16) | (0x7L << 12) | (randomA & 0x0FFFL);
        long leastSigBits = (0x2L << 62) | (randomB & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(mostSigBits, leastSigBits);
    }

    public static String uuidV7String() {
        return uuidV7().toString();
    }

    public static String uuidV7Compact() {
        return uuidV7().toString().replace("-", "");
    }

    public static boolean isValid(String text) {
        if (text == null) {
            return false;
        }
        try {
            UUID.fromString(text);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static UUID fromCompact(String text) {
        if (text == null || text.length() != 32) {
            throw new IllegalArgumentException("紧凑格式 UUID 必须为 32 位");
        }
        return UUID.fromString(text.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }
}
