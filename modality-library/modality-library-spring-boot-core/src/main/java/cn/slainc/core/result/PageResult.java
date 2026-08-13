package cn.slainc.core.result;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> records, long total, long current, long size, long pages) {

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        long safeSize = size <= 0 ? 0 : size;
        long safeCurrent = current <= 0 ? 1 : current;
        long pages = safeSize == 0 ? 0 : (total + safeSize - 1) / safeSize;
        return new PageResult<>(records == null ? new ArrayList<>() : records, total, safeCurrent, safeSize, pages);
    }

    public static <T> PageResult<T> empty(long current, long size) {
        return of(new ArrayList<>(), 0L, current, size);
    }

    public static <T> PageResult<T> from(List<T> all, long current, long size) {
        if (all == null || all.isEmpty() || size <= 0) {
            return of(new ArrayList<>(), all == null ? 0L : all.size(), current, size);
        }
        List<T> source = all;
        int fromIndex = (int) Math.min(source.size(), Math.max(0L, (current - 1) * size));
        int toIndex = (int) Math.min(source.size(), fromIndex + size);
        return of(new ArrayList<>(source.subList(fromIndex, toIndex)), source.size(), current, size);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = new ArrayList<>(records.size());
        for (T record : records) {
            mapped.add(mapper.apply(record));
        }
        return new PageResult<>(mapped, total, current, size, pages);
    }

    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }

    public boolean hasNext() {
        return current < pages;
    }
}
