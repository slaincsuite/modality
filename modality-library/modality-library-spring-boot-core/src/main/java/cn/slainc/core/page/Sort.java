package cn.slainc.core.page;

import java.io.Serializable;

public record Sort(String field, Direction direction) implements Serializable {

    public enum Direction {
        ASC, DESC
    }

    public static Sort of(String field, Direction direction) {
        return new Sort(field, direction == null ? Direction.ASC : direction);
    }

    public static Sort asc(String field) {
        return new Sort(field, Direction.ASC);
    }

    public static Sort desc(String field) {
        return new Sort(field, Direction.DESC);
    }

    public boolean descending() {
        return direction == Direction.DESC;
    }
}
