package cn.slainc.core.page;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public record PageParam(long current, long size, List<Sort> sorts) implements Serializable {

    public static final long DEFAULT_SIZE = 10L;
    public static final long MAX_SIZE = 500L;

    public static PageParam of(long current, long size) {
        return new PageParam(current, size, new ArrayList<>());
    }

    public static PageParam defaults() {
        return new PageParam(1L, DEFAULT_SIZE, new ArrayList<>());
    }

    public PageParam normalized() {
        long safeCurrent = current <= 0 ? 1L : current;
        long safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageParam(safeCurrent, safeSize, sorts == null ? new ArrayList<>() : sorts);
    }

    public long offset() {
        return Math.max(0L, (current - 1) * size);
    }

    public boolean hasSort() {
        return sorts != null && !sorts.isEmpty();
    }

    public PageParam withSort(Sort sort) {
        List<Sort> merged = sorts == null ? new ArrayList<>() : new ArrayList<>(sorts);
        if (sort != null) {
            merged.add(sort);
        }
        return new PageParam(current, size, merged);
    }
}
