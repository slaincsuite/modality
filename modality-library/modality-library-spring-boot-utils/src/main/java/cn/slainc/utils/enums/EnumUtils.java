package cn.slainc.utils.enums;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class EnumUtils {

    private EnumUtils() {
    }

    public static <E extends Enum<E>> E getEnum(Class<E> type, String name) {
        if (type == null || name == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static <E extends Enum<E>> E getEnumIgnoreCase(Class<E> type, String name) {
        if (type == null || name == null) {
            return null;
        }
        String target = name.trim();
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(target)) {
                return constant;
            }
        }
        return null;
    }

    public static <E extends Enum<E>> E getEnumByValue(Class<E> type, Object value, Function<E, Object> valueExtractor) {
        if (type == null || valueExtractor == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            Object candidate = valueExtractor.apply(constant);
            if (candidate == null ? value == null : candidate.equals(value)) {
                return constant;
            }
        }
        return null;
    }

    public static <E extends Enum<E>> E getEnumByValueIgnoreCase(Class<E> type, String value,
                                                                Function<E, String> valueExtractor) {
        if (type == null || value == null || valueExtractor == null) {
            return null;
        }
        String target = value.trim();
        for (E constant : type.getEnumConstants()) {
            String candidate = valueExtractor.apply(constant);
            if (candidate != null && candidate.equalsIgnoreCase(target)) {
                return constant;
            }
        }
        return null;
    }

    public static <E extends Enum<E>> Map<String, E> toMap(Class<E> type) {
        Map<String, E> result = new LinkedHashMap<>();
        if (type == null) {
            return result;
        }
        for (E constant : type.getEnumConstants()) {
            result.put(constant.name(), constant);
        }
        return result;
    }

    public static <E extends Enum<E>> Map<Object, String> toDict(Class<E> type, Function<E, Object> valueExtractor) {
        Map<Object, String> result = new LinkedHashMap<>();
        if (type == null) {
            return result;
        }
        for (E constant : type.getEnumConstants()) {
            result.put(valueExtractor.apply(constant), constant.name());
        }
        return result;
    }

    public static <E extends Enum<E>> Map<Object, String> toDict(Class<E> type,
                                                                 Function<E, Object> valueExtractor,
                                                                 Function<E, String> labelExtractor) {
        Map<Object, String> result = new LinkedHashMap<>();
        if (type == null) {
            return result;
        }
        for (E constant : type.getEnumConstants()) {
            result.put(valueExtractor.apply(constant), labelExtractor.apply(constant));
        }
        return result;
    }

    public static <E extends Enum<E>> List<String> names(Class<E> type) {
        List<String> result = new ArrayList<>();
        if (type == null) {
            return result;
        }
        for (E constant : type.getEnumConstants()) {
            result.add(constant.name());
        }
        return result;
    }

    public static <E extends Enum<E>> boolean contains(Class<E> type, String name) {
        return getEnum(type, name) != null;
    }
}
