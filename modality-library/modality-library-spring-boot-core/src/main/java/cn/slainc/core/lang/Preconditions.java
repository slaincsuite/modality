package cn.slainc.core.lang;

public final class Preconditions {

    private Preconditions() {
    }

    public static void checkArgument(boolean expression, String messageTemplate, Object... args) {
        if (!expression) {
            throw new IllegalArgumentException(format(messageTemplate, args));
        }
    }

    public static <T> T checkNotNull(T value, String messageTemplate, Object... args) {
        if (value == null) {
            throw new NullPointerException(format(messageTemplate, args));
        }
        return value;
    }

    public static void checkState(boolean expression, String messageTemplate, Object... args) {
        if (!expression) {
            throw new IllegalStateException(format(messageTemplate, args));
        }
    }

    public static int checkIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("下标 " + index + " 超出范围 [0, " + size + ")");
        }
        return index;
    }

    public static int checkPositionIndex(int index, int size) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("位置 " + index + " 超出范围 [0, " + size + "]");
        }
        return index;
    }

    private static String format(String template, Object[] args) {
        if (template == null || args == null || args.length == 0) {
            return template;
        }
        return String.format(template, args);
    }
}
