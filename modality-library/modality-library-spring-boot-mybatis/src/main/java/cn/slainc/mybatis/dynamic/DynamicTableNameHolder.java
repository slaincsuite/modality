package cn.slainc.mybatis.dynamic;

import java.util.HashMap;
import java.util.Map;

public final class DynamicTableNameHolder {

    private static final ThreadLocal<Map<String, String>> HOLDER = new ThreadLocal<>();

    private DynamicTableNameHolder() {
    }

    public static void set(String logicTable, String actualTable) {
        Map<String, String> map = HOLDER.get();
        if (map == null) {
            map = new HashMap<>();
            HOLDER.set(map);
        }
        map.put(logicTable, actualTable);
    }

    public static String get(String logicTable) {
        Map<String, String> map = HOLDER.get();
        return map == null ? null : map.get(logicTable);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static <T> T callWith(String logicTable, String actualTable, java.util.function.Supplier<T> supplier) {
        set(logicTable, actualTable);
        try {
            return supplier.get();
        } finally {
            clear();
        }
    }
}
