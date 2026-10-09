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
        return new PageParam(safeCurrent(), safeSize(), sorts == null ? new ArrayList<>() : sorts);
    }

    public long offset() {
        long pages = safeCurrent() - 1L;
        if (pages <= 0L) {
            return 0L;
        }
        // 页号接近 Long.MAX_VALUE 时相乘会溢出成负数，偏移量必须饱和而不是回绕
        return pages > Long.MAX_VALUE / safeSize() ? Long.MAX_VALUE : pages * safeSize();
    }

    private long safeCurrent() {
        return current <= 0 ? 1L : current;
    }

    private long safeSize() {
        return size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
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
