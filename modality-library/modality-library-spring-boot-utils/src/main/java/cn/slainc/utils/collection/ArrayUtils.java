package cn.slainc.utils.collection;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class ArrayUtils {

    private ArrayUtils() {
    }

    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNotEmpty(Object[] array) {
        return !isEmpty(array);
    }

    public static boolean isEmpty(int[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isEmpty(long[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isEmpty(byte[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNotEmpty(byte[] array) {
        return !isEmpty(array);
    }

    public static <T> List<T> toList(T[] array) {
        return isEmpty(array) ? new ArrayList<>() : new ArrayList<>(Arrays.asList(array));
    }

    public static List<Integer> toList(int[] array) {
        List<Integer> result = new ArrayList<>();
        if (array != null) {
            for (int value : array) {
                result.add(value);
            }
        }
        return result;
    }

    public static List<Long> toList(long[] array) {
        List<Long> result = new ArrayList<>();
        if (array != null) {
            for (long value : array) {
                result.add(value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Collection<T> collection, Class<T> componentType) {
        if (collection == null) {
            return (T[]) Array.newInstance(componentType, 0);
        }
        T[] array = (T[]) Array.newInstance(componentType, collection.size());
        return collection.toArray(array);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] subArray(T[] array, int fromIndex, int toIndex) {
        if (isEmpty(array)) {
            return array;
        }
        int from = Math.max(0, fromIndex);
        int to = Math.min(array.length, Math.max(from, toIndex));
        return Arrays.copyOfRange(array, from, to);
    }

    public static byte[] subArray(byte[] array, int fromIndex, int toIndex) {
        if (isEmpty(array)) {
            return array;
        }
        int from = Math.max(0, fromIndex);
        int to = Math.min(array.length, Math.max(from, toIndex));
        return Arrays.copyOfRange(array, from, to);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] concat(T[] left, T[] right, Class<T> componentType) {
        int leftLength = left == null ? 0 : left.length;
        int rightLength = right == null ? 0 : right.length;
        T[] result = (T[]) Array.newInstance(componentType, leftLength + rightLength);
        if (leftLength > 0) {
            System.arraycopy(left, 0, result, 0, leftLength);
        }
        if (rightLength > 0) {
            System.arraycopy(right, 0, result, leftLength, rightLength);
        }
        return result;
    }

    public static byte[] concat(byte[] left, byte[] right) {
        int leftLength = left == null ? 0 : left.length;
        int rightLength = right == null ? 0 : right.length;
        byte[] result = new byte[leftLength + rightLength];
        if (leftLength > 0) {
            System.arraycopy(left, 0, result, 0, leftLength);
        }
        if (rightLength > 0) {
            System.arraycopy(right, 0, result, leftLength, rightLength);
        }
        return result;
    }

    public static <T> boolean contains(T[] array, T value) {
        if (isEmpty(array)) {
            return false;
        }
        for (T item : array) {
            if (item == null ? value == null : item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static <T> T first(T[] array) {
        return isEmpty(array) ? null : array[0];
    }

    public static <T> T last(T[] array) {
        return isEmpty(array) ? null : array[array.length - 1];
    }
}
