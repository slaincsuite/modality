package cn.slainc.core.page;

import java.io.Serializable;

public record Cursor(String field, Object value, Direction direction, int size) implements Serializable {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public enum Direction {
        NEXT, PREVIOUS
    }

    public static Cursor first(String field, int size) {
        return new Cursor(field, null, Direction.NEXT, size);
    }

    public Cursor normalized() {
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new Cursor(field, value, direction == null ? Direction.NEXT : direction, safeSize);
    }

    public boolean isFirstPage() {
        return value == null;
    }

    public boolean forward() {
        return direction == Direction.NEXT;
    }
}
