package cn.slainc.excel.converter;

import cn.slainc.core.enums.DictEnum;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class DictRegistry {

    public record Entry(Function<Object, String> toLabel, Function<String, Object> toValue) {
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private DictRegistry() {
    }

    public static void register(String name, Function<Object, String> toLabel, Function<String, Object> toValue) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("字典名称不能为空");
        }
        ENTRIES.put(name, new Entry(toLabel, toValue));
    }

    public static <E extends Enum<E>> void registerEnum(String name, Class<E> type) {
        Map<String, E> byLabel = new LinkedHashMap<>();
        Map<String, E> byValue = new LinkedHashMap<>();
        for (E constant : type.getEnumConstants()) {
            if (constant instanceof DictEnum dict) {
                Object value = dict.value();
                if (value != null) {
                    byValue.put(String.valueOf(value), constant);
                }
                byLabel.put(dict.label(), constant);
            } else {
                byLabel.put(constant.name(), constant);
            }
            byValue.putIfAbsent(constant.name(), constant);
        }
        register(name,
                value -> {
                    if (value == null) {
                        return null;
                    }
                    if (value instanceof Enum<?> constant) {
                        return constant instanceof DictEnum dict ? dict.label() : constant.name();
                    }
                    E matched = byValue.get(String.valueOf(value));
                    return matched == null ? null : matched.name();
                },
                label -> {
                    if (label == null) {
                        return null;
                    }
                    E matched = byLabel.get(label.trim());
                    return matched != null ? matched : byValue.get(label.trim());
                });
    }

    public static Entry get(String name) {
        return name == null ? null : ENTRIES.get(name);
    }

    public static boolean contains(String name) {
        return name != null && ENTRIES.containsKey(name);
    }

    public static String toLabel(String name, Object value) {
        Entry entry = get(name);
        return entry == null ? null : entry.toLabel().apply(value);
    }

    public static Object toValue(String name, String label) {
        Entry entry = get(name);
        return entry == null ? null : entry.toValue().apply(label);
    }

    public static void remove(String name) {
        ENTRIES.remove(name);
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
