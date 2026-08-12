package cn.slainc.utils.collection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class MapUtils {

    private MapUtils() {
    }

    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    public static <K, V> Map<K, V> emptyIfNull(Map<K, V> map) {
        return map == null ? new LinkedHashMap<>() : map;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Map<?, ?> map, String path) {
        if (map == null || path == null || path.isEmpty()) {
            return null;
        }
        Object current = map;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(segment);
            if (current == null) {
                return null;
            }
        }
        return (T) current;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Map<?, ?> map, String path, T defaultValue) {
        T value = get(map, path);
        return value == null ? defaultValue : value;
    }

    @SuppressWarnings("unchecked")
    public static void put(Map<String, Object> map, String path, Object value) {
        if (map == null || path == null || path.isEmpty()) {
            return;
        }
        String[] segments = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < segments.length - 1; i++) {
            Object next = current.get(segments[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(segments[i], next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(segments[segments.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepMerge(Map<String, Object> target, Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (target != null) {
            result.putAll(target);
        }
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object existing = result.get(entry.getKey());
            Object incoming = entry.getValue();
            if (existing instanceof Map && incoming instanceof Map) {
                result.put(entry.getKey(), deepMerge((Map<String, Object>) existing, (Map<String, Object>) incoming));
            } else {
                result.put(entry.getKey(), incoming);
            }
        }
        return result;
    }

    public static <K extends Comparable<K>, V> Map<K, V> sortByKey(Map<K, V> map) {
        if (isEmpty(map)) {
            return new LinkedHashMap<>();
        }
        return new TreeMap<>(map);
    }

    public static <K, V extends Comparable<V>> Map<K, V> sortByValue(Map<K, V> map) {
        Map<K, V> result = new LinkedHashMap<>();
        if (isEmpty(map)) {
            return result;
        }
        List<Map.Entry<K, V>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getValue));
        for (Map.Entry<K, V> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static <K, V> Map<K, V> invert(Map<V, K> map) {
        Map<K, V> result = new LinkedHashMap<>();
        if (isEmpty(map)) {
            return result;
        }
        for (Map.Entry<V, K> entry : map.entrySet()) {
            result.put(entry.getValue(), entry.getKey());
        }
        return result;
    }

    public static Map<String, Object> flatten(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        flattenInto(map, "", result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void flattenInto(Map<String, Object> map, String prefix, Map<String, Object> result) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenInto((Map<String, Object>) value, key, result);
            } else {
                result.put(key, value);
            }
        }
    }
}
