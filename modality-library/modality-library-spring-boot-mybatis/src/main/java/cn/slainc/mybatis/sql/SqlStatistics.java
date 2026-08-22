package cn.slainc.mybatis.sql;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class SqlStatistics {

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    private final Map<String, LongAdder> elapsedByType = new ConcurrentHashMap<>();

    private final List<SlowSqlRecord> slowQueries = new ArrayList<>();

    private final LongAdder total = new LongAdder();

    private final int slowTopSize;

    public SqlStatistics(int slowTopSize) {
        this.slowTopSize = Math.max(slowTopSize, 1);
    }

    public void record(String commandType, long elapsedMillis, SlowSqlRecord slowRecord) {
        total.increment();
        counters.computeIfAbsent(commandType, key -> new LongAdder()).increment();
        elapsedByType.computeIfAbsent(commandType, key -> new LongAdder()).add(elapsedMillis);
        if (slowRecord != null) {
            synchronized (slowQueries) {
                slowQueries.add(slowRecord);
                slowQueries.sort(Comparator.comparingLong(SlowSqlRecord::elapsedMillis).reversed());
                while (slowQueries.size() > slowTopSize) {
                    slowQueries.remove(slowQueries.size() - 1);
                }
            }
        }
    }

    public long total() {
        return total.sum();
    }

    public Map<String, Long> countByType() {
        Map<String, Long> result = new LinkedHashMap<>();
        counters.forEach((type, adder) -> result.put(type, adder.sum()));
        return result;
    }

    public Map<String, Long> averageElapsedByType() {
        Map<String, Long> result = new LinkedHashMap<>();
        elapsedByType.forEach((type, adder) -> {
            long count = counters.getOrDefault(type, new LongAdder()).sum();
            result.put(type, count == 0 ? 0L : adder.sum() / count);
        });
        return result;
    }

    public List<SlowSqlRecord> topSlow() {
        synchronized (slowQueries) {
            return List.copyOf(slowQueries);
        }
    }

    public void reset() {
        counters.clear();
        elapsedByType.clear();
        synchronized (slowQueries) {
            slowQueries.clear();
        }
        total.reset();
    }
}
