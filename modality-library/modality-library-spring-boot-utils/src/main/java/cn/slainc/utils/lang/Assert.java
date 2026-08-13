package cn.slainc.utils.lang;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

public final class Assert {

    private Assert() {
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void isTrue(boolean expression, Supplier<? extends RuntimeException> exceptionSupplier) {
        if (!expression) {
            throw exceptionSupplier.get();
        }
    }

    public static void isFalse(boolean expression, String message) {
        isTrue(!expression, message);
    }

    public static <T> T notNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static <T> T notNull(T value, Supplier<? extends RuntimeException> exceptionSupplier) {
        if (value == null) {
            throw exceptionSupplier.get();
        }
        return value;
    }

    public static String hasText(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    public static String hasText(String text, Supplier<? extends RuntimeException> exceptionSupplier) {
        if (text == null || text.isBlank()) {
            throw exceptionSupplier.get();
        }
        return text;
    }

    public static <T extends Collection<?>> T notEmpty(T collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    public static <K, V> Map<K, V> notEmpty(Map<K, V> map, String message) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return map;
    }

    public static <T> T[] notEmpty(T[] array, String message) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(message);
        }
        return array;
    }

    public static long inRange(long value, long min, long max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static int inRange(int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static void isPositive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void notNegative(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public static <T> T state(T value, boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    public static void state(boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException(message);
        }
    }
}
