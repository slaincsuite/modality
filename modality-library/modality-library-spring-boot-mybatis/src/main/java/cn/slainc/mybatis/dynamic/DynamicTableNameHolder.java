package cn.slainc.mybatis.dynamic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class DynamicTableNameHolder {

    private static final ThreadLocal<Map<String, String>> HOLDER = new ThreadLocal<>();

    private DynamicTableNameHolder() {
    }

    public static String get(String logicTable) {
        Map<String, String> map = HOLDER.get();
        return map == null ? null : map.get(logicTable);
    }

    public static void set(String logicTable, String actualTable) {
        if (logicTable == null || logicTable.isBlank()) {
            return;
        }
        Map<String, String> map = HOLDER.get();
        if (map == null) {
            map = new HashMap<>();
            HOLDER.set(map);
        }
        if (actualTable == null || actualTable.isBlank()) {
            map.remove(logicTable);
        } else {
            map.put(logicTable, actualTable);
        }
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static void run(String logicTable, String actualTable, Runnable action) {
        call(logicTable, actualTable, () -> {
            action.run();
            return null;
        });
    }

    // 嵌套调用在外层映射的副本上叠加，直接改原表会让内层映射在本次调用结束后仍然生效
    public static <T> T call(String logicTable, String actualTable, Supplier<T> action) {
        Map<String, String> previous = HOLDER.get();
        HOLDER.set(previous == null ? new HashMap<>() : new HashMap<>(previous));
        set(logicTable, actualTable);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }
}
