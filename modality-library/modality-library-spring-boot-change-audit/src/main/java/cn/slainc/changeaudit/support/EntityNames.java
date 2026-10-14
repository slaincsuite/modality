package cn.slainc.changeaudit.support;

import cn.slainc.utils.reflect.ReflectUtils;
import cn.slainc.utils.string.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class EntityNames {

    private static final List<String> SUFFIXES = List.of("Entity", "Model", "DTO", "Vo", "VO", "DO");

    private static final List<String> FALLBACKS = List.of("title", "label", "code");

    private EntityNames() {
    }

    public static String of(Object entity) {
        if (entity == null) {
            return null;
        }
        for (String candidate : candidates(entity.getClass())) {
            String value = read(entity, candidate);
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static List<String> candidates(Class<?> type) {
        List<String> candidates = new ArrayList<>();
        candidates.add("name");
        candidates.add(StringUtils.uncapitalize(strip(entityName(type))) + "Name");
        candidates.addAll(FALLBACKS);
        return candidates;
    }

    private static String entityName(Class<?> type) {
        String simpleName = type.getSimpleName();
        return StringUtils.isBlank(simpleName) ? type.getName() : simpleName;
    }

    private static String strip(String simpleName) {
        for (String suffix : SUFFIXES) {
            if (simpleName.length() > suffix.length() && simpleName.endsWith(suffix)) {
                return simpleName.substring(0, simpleName.length() - suffix.length());
            }
        }
        return simpleName;
    }

    private static String read(Object entity, String property) {
        Class<?> type = entity.getClass();
        try {
            Method getter = getterOf(type, "get" + StringUtils.capitalize(property));
            if (getter != null) {
                Object value = getter.invoke(entity);
                return value instanceof CharSequence sequence ? sequence.toString() : null;
            }
            if (ReflectUtils.getAllFields(type).stream().anyMatch(field -> field.getName().equals(property))) {
                Object value = ReflectUtils.getFieldValue(entity, property);
                return value instanceof CharSequence sequence ? sequence.toString() : null;
            }
        } catch (RuntimeException | ReflectiveOperationException e) {
            // 读取失败时返回 null，由调用方按“无值”处理
            return null;
        }
        return null;
    }

    private static Method getterOf(Class<?> type, String name) {
        for (Method candidate : ReflectUtils.getAllMethods(type)) {
            if (candidate.getName().equals(name) && candidate.getParameterCount() == 0) {
                candidate.trySetAccessible();
                return candidate;
            }
        }
        return null;
    }
}
