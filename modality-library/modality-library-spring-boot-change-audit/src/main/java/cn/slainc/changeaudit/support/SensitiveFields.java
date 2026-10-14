package cn.slainc.changeaudit.support;

import cn.slainc.mybatis.annotation.Sensitive;
import cn.slainc.mybatis.interceptor.FieldSecurityInterceptor;
import cn.slainc.utils.reflect.ReflectUtils;
import cn.slainc.utils.string.StringUtils;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SensitiveFields {

    private static final Map<Class<?>, Map<String, Sensitive>> CACHE = new ConcurrentHashMap<>();

    private SensitiveFields() {
    }

    public static Map<String, Sensitive> of(Class<?> type) {
        if (type == null) {
            return Map.of();
        }
        return CACHE.computeIfAbsent(type, SensitiveFields::scan);
    }

    public static Sensitive find(Class<?> type, String fieldName) {
        if (type == null || StringUtils.isBlank(fieldName)) {
            return null;
        }
        return of(type).get(fieldName);
    }

    public static String mask(Class<?> type, String fieldName, String value) {
        Sensitive sensitive = find(type, fieldName);
        return sensitive == null || value == null ? value : FieldSecurityInterceptor.mask(value, sensitive);
    }

    private static Map<String, Sensitive> scan(Class<?> type) {
        Map<String, Sensitive> strategies = new LinkedHashMap<>();
        for (Field field : ReflectUtils.getAllFields(type)) {
            Sensitive sensitive = field.getAnnotation(Sensitive.class);
            if (sensitive != null) {
                strategies.put(field.getName(), sensitive);
            }
        }
        return Map.copyOf(strategies);
    }
}
