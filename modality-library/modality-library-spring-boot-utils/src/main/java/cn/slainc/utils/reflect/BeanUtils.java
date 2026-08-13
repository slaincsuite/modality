package cn.slainc.utils.reflect;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BeanUtils {

    private BeanUtils() {
    }

    public static void copyProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        for (Field field : ReflectUtils.getAllFields(target.getClass())) {
            if (Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            Field sourceField = findField(source.getClass(), field.getName());
            if (sourceField == null) {
                continue;
            }
            try {
                Object value = sourceField.get(source);
                field.set(target, ReflectUtils.convert(value, field.getType()));
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("拷贝字段失败: " + field.getName(), ex);
            }
        }
    }

    public static <T> T copyProperties(Object source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        T target = ReflectUtils.newInstance(targetType);
        copyProperties(source, target);
        return target;
    }

    public static Map<String, Object> beanToMap(Object bean) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (bean == null) {
            return result;
        }
        for (Field field : ReflectUtils.getAllFields(bean.getClass())) {
            try {
                result.put(field.getName(), field.get(bean));
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("读取字段失败: " + field.getName(), ex);
            }
        }
        return result;
    }

    public static Map<String, Object> beanToMap(Object bean, boolean ignoreNull) {
        Map<String, Object> result = beanToMap(bean);
        if (ignoreNull) {
            result.values().removeIf(java.util.Objects::isNull);
        }
        return result;
    }

    public static <T> T mapToBean(Map<String, Object> map, Class<T> targetType) {
        T target = ReflectUtils.newInstance(targetType);
        if (map == null || map.isEmpty()) {
            return target;
        }
        for (Field field : ReflectUtils.getAllFields(targetType)) {
            if (!map.containsKey(field.getName())) {
                continue;
            }
            try {
                field.set(target, ReflectUtils.convert(map.get(field.getName()), field.getType()));
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("写入字段失败: " + field.getName(), ex);
            }
        }
        return target;
    }

    public static <T> T deepCopy(T source) {
        return deepCopy(source, new IdentityHashMap<>());
    }

    public static List<FieldDiff> diff(Object left, Object right) {
        List<FieldDiff> diffs = new ArrayList<>();
        if (left == null && right == null) {
            return diffs;
        }
        Class<?> type = left == null ? right.getClass() : left.getClass();
        for (Field field : ReflectUtils.getAllFields(type)) {
            try {
                Object leftValue = left == null ? null : field.get(left);
                Object rightValue = right == null ? null : field.get(right);
                if (!java.util.Objects.equals(leftValue, rightValue)) {
                    diffs.add(new FieldDiff(field.getName(), leftValue, rightValue));
                }
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("字段差异比对失败: " + field.getName(), ex);
            }
        }
        return diffs;
    }

    @SuppressWarnings("unchecked")
    private static <T> T deepCopy(T source, IdentityHashMap<Object, Object> visited) {
        if (source == null) {
            return null;
        }
        Class<?> type = source.getClass();
        if (isImmutable(type)) {
            return source;
        }
        Object cached = visited.get(source);
        if (cached != null) {
            return (T) cached;
        }
        if (type.isArray()) {
            int length = Array.getLength(source);
            Object copy = Array.newInstance(type.getComponentType(), length);
            visited.put(source, copy);
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, deepCopy(Array.get(source, i), visited));
            }
            return (T) copy;
        }
        if (source instanceof Collection<?> collection) {
            Collection<Object> copy = newCollection(collection);
            visited.put(source, copy);
            for (Object item : collection) {
                copy.add(deepCopy(item, visited));
            }
            return (T) copy;
        }
        if (source instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            visited.put(source, copy);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(deepCopy(entry.getKey(), visited), deepCopy(entry.getValue(), visited));
            }
            return (T) copy;
        }
        T copy = ReflectUtils.newInstance((Class<T>) type);
        visited.put(source, copy);
        for (Field field : ReflectUtils.getAllFields(type)) {
            if (Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            try {
                field.set(copy, deepCopy(field.get(source), visited));
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("拷贝字段失败: " + field.getName(), ex);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> newCollection(Collection<?> source) {
        if (source instanceof java.util.Set) {
            return new java.util.LinkedHashSet<>();
        }
        return new ArrayList<>();
    }

    private static boolean isImmutable(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || type == String.class
                || type == Boolean.class
                || type == Character.class
                || Number.class.isAssignableFrom(type)
                || type == Date.class
                || Temporal.class.isAssignableFrom(type)
                || type == Class.class;
    }

    private static Field findField(Class<?> type, String name) {
        try {
            return ReflectUtils.getField(type, name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record FieldDiff(String field, Object left, Object right) {
    }
}
