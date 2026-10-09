package cn.slainc.datasource.strategy;

import com.baomidou.dynamic.datasource.strategy.DynamicDataSourceStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class WeightedDynamicDataSourceStrategy implements DynamicDataSourceStrategy {

    public static final int DEFAULT_WEIGHT = 1;

    private static final Map<String, Integer> WEIGHTS = new ConcurrentHashMap<>();

    private static final Map<String, AtomicInteger> CURSORS = new ConcurrentHashMap<>();

    private static volatile boolean weighted = false;

    public static void setWeight(String dataSourceName, int weight) {
        if (weight <= 0) {
            WEIGHTS.remove(dataSourceName);
        } else {
            WEIGHTS.put(dataSourceName, weight);
        }
        weighted = !WEIGHTS.isEmpty();
    }

    public static void removeWeight(String dataSourceName) {
        WEIGHTS.remove(dataSourceName);
        CURSORS.keySet().removeIf(key -> names(key).contains(dataSourceName));
        weighted = !WEIGHTS.isEmpty();
    }

    public static int weightOf(String dataSourceName) {
        return WEIGHTS.getOrDefault(dataSourceName, DEFAULT_WEIGHT);
    }

    public static void clear() {
        WEIGHTS.clear();
        CURSORS.clear();
        weighted = false;
    }

    @Override
    public String determineKey(List<String> dataSourceNames) {
        if (dataSourceNames == null || dataSourceNames.isEmpty()) {
            return null;
        }
        if (dataSourceNames.size() == 1 || !weighted) {
            return dataSourceNames.get(ThreadLocalRandom.current().nextInt(dataSourceNames.size()));
        }
        int total = 0;
        int[] weights = new int[dataSourceNames.size()];
        for (int i = 0; i < dataSourceNames.size(); i++) {
            weights[i] = weightOf(dataSourceNames.get(i));
            total += weights[i];
        }
        if (total <= 0) {
            return dataSourceNames.get(0);
        }
        int index = Math.floorMod(smoothIndex(dataSourceNames), total);
        for (int i = 0; i < dataSourceNames.size(); i++) {
            index -= weights[i];
            if (index < 0) {
                return dataSourceNames.get(i);
            }
        }
        return dataSourceNames.get(0);
    }

    static Set<String> cursorKeys() {
        return Set.copyOf(CURSORS.keySet());
    }

    private int smoothIndex(List<String> dataSourceNames) {
        AtomicInteger cursor = CURSORS.computeIfAbsent(cursorKey(dataSourceNames), key -> new AtomicInteger());
        return cursor.getAndIncrement();
    }

    // 游标按分组稳定命名，用 List.toString() 当键会随分组内容变化而堆积，移除数据源时也清不掉
    private static String cursorKey(List<String> dataSourceNames) {
        List<String> sorted = new ArrayList<>(dataSourceNames);
        Collections.sort(sorted);
        return String.join(",", sorted);
    }

    private static List<String> names(String cursorKey) {
        return List.of(cursorKey.split(","));
    }
}
