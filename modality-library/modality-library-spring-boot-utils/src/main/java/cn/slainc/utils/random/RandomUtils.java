package cn.slainc.utils.random;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomUtils {

    public static final String DIGITS = "0123456789";
    public static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    public static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LETTERS = LOWER + UPPER;
    public static final String ALPHANUMERIC = DIGITS + LETTERS;
    public static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RandomUtils() {
    }

    public static int randomInt(int minInclusive, int maxExclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxExclusive);
    }

    public static long randomLong(long minInclusive, long maxExclusive) {
        return ThreadLocalRandom.current().nextLong(minInclusive, maxExclusive);
    }

    public static double randomDouble(double minInclusive, double maxExclusive) {
        return ThreadLocalRandom.current().nextDouble(minInclusive, maxExclusive);
    }

    public static boolean randomBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    public static String randomNumeric(int length) {
        return randomString(length, DIGITS);
    }

    public static String randomAlphabetic(int length) {
        return randomString(length, LETTERS);
    }

    public static String randomAlphanumeric(int length) {
        return randomString(length, ALPHANUMERIC);
    }

    public static String randomString(int length, String charset) {
        if (length <= 0) {
            return "";
        }
        if (charset == null || charset.isEmpty()) {
            throw new IllegalArgumentException("字符集不能为空");
        }
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(charset.charAt(SECURE_RANDOM.nextInt(charset.length())));
        }
        return builder.toString();
    }

    public static byte[] randomBytes(int length) {
        if (length <= 0) {
            return new byte[0];
        }
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static <T> T randomElement(Collection<T> collection) {
        if (collection == null || collection.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(collection.size());
        int current = 0;
        for (T item : collection) {
            if (current++ == index) {
                return item;
            }
        }
        return null;
    }

    public static <T> List<T> shuffle(Collection<T> collection) {
        List<T> result = collection == null ? new ArrayList<>() : new ArrayList<>(collection);
        java.util.Collections.shuffle(result, ThreadLocalRandom.current());
        return result;
    }

    public static <T> List<T> sample(Collection<T> collection, int count) {
        List<T> shuffled = shuffle(collection);
        if (count <= 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }
}
