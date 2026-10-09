package cn.slainc.mybatis.interceptor;

import cn.slainc.mybatis.annotation.Sensitive;
import cn.slainc.mybatis.annotation.SensitiveType;
import cn.slainc.mybatis.crypto.FieldEncryptor;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class FieldSecurityInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(FieldSecurityInterceptor.class);

    private final FieldEncryptor encryptor;

    private final boolean maskingEnabled;

    public FieldSecurityInterceptor(FieldEncryptor encryptor, boolean maskingEnabled) {
        this.encryptor = encryptor;
        this.maskingEnabled = maskingEnabled;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        boolean update = invocation.getArgs().length == 2;
        List<Runnable> restores = new ArrayList<>();
        if (encryptor.isEnabled()) {
            encrypt(invocation.getArgs()[1], new IdentityHashMap<>(), restores);
        }
        Object result;
        try {
            result = invocation.proceed();
        } finally {
            for (Runnable restore : restores) {
                restore.run();
            }
        }
        if (!update && result != null) {
            decryptResults(result);
        }
        return result;
    }

    private void encrypt(Object parameter, IdentityHashMap<Object, Boolean> visited, List<Runnable> restores) {
        if (parameter == null || visited.put(parameter, Boolean.TRUE) != null) {
            return;
        }
        if (parameter instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                encrypt(value, visited, restores);
            }
            return;
        }
        if (parameter instanceof Collection<?> collection) {
            for (Object value : collection) {
                encrypt(value, visited, restores);
            }
            return;
        }
        if (parameter.getClass().isArray()) {
            for (Object value : (Object[]) parameter) {
                encrypt(value, visited, restores);
            }
            return;
        }
        if (isSimple(parameter.getClass())) {
            return;
        }
        for (Field field : fieldsOf(parameter.getClass())) {
            if (!FieldEncryptor.isEncryptedField(field)) {
                continue;
            }
            Object value = readField(field, parameter);
            if (!(value instanceof String text) || text.isEmpty()) {
                continue;
            }
            String encrypted = encryptor.encrypt(text);
            writeField(field, parameter, encrypted);
            restores.add(() -> writeField(field, parameter, text));
        }
    }

    private void decryptResults(Object result) {
        if (result instanceof Collection<?> collection) {
            for (Object item : collection) {
                decryptResults(item);
            }
            return;
        }
        if (result instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                decryptResults(value);
            }
            return;
        }
        if (isSimple(result.getClass())) {
            return;
        }
        for (Field field : fieldsOf(result.getClass())) {
            if (FieldEncryptor.isEncryptedField(field)) {
                Object value = readField(field, result);
                if (value instanceof String text && !text.isEmpty()) {
                    writeField(field, result, encryptor.decrypt(text));
                }
            }
            if (maskingEnabled && field.isAnnotationPresent(Sensitive.class)) {
                Object value = readField(field, result);
                if (value instanceof String text && !text.isEmpty()) {
                    writeField(field, result, mask(text, field.getAnnotation(Sensitive.class)));
                }
            }
        }
    }

    public static String mask(String value, Sensitive sensitive) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        SensitiveType type = sensitive.type();
        if (type == SensitiveType.PASSWORD) {
            return repeat(sensitive.mask(), 6);
        }
        if (type != SensitiveType.DEFAULT) {
            return builtin(value, type, sensitive.mask());
        }
        int prefix = sensitive.prefixKeep();
        int suffix = sensitive.suffixKeep();
        if (prefix + suffix >= value.length()) {
            return repeat(sensitive.mask(), value.length());
        }
        return value.substring(0, prefix) + repeat(sensitive.mask(), value.length() - prefix - suffix)
                + (suffix == 0 ? "" : value.substring(value.length() - suffix));
    }

    private static String builtin(String value, SensitiveType type, String mask) {
        return switch (type) {
            case NAME -> value.length() <= 1 ? value : value.charAt(0) + repeat(mask, value.length() - 1);
            case ID_CARD -> value.length() < 8 ? repeat(mask, value.length())
                    : value.substring(0, 4) + repeat(mask, value.length() - 8) + value.substring(value.length() - 4);
            case PHONE -> value.length() < 7 ? repeat(mask, value.length())
                    : value.substring(0, 3) + repeat(mask, value.length() - 7) + value.substring(value.length() - 4);
            case EMAIL -> email(value, mask);
            case BANK_CARD -> value.length() < 8 ? repeat(mask, value.length())
                    : value.substring(0, 4) + repeat(mask, value.length() - 8) + value.substring(value.length() - 4);
            case ADDRESS -> value.length() <= 6 ? repeat(mask, value.length()) : value.substring(0, 6) + repeat(mask, 3);
            default -> repeat(mask, value.length());
        };
    }

    private static String email(String value, String mask) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return repeat(mask, value.length());
        }
        String name = value.substring(0, at);
        String masked = name.length() <= 1 ? name : name.charAt(0) + repeat(mask, Math.min(name.length() - 1, 3));
        return masked + value.substring(at);
    }

    private static String repeat(String mask, int times) {
        if (times <= 0) {
            return "";
        }
        String unit = mask == null || mask.isEmpty() ? "*" : mask;
        return unit.repeat(Math.min(times, 64));
    }

    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static Object readField(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            log.debug("读取字段 {} 失败: {}", field.getName(), exception.getMessage());
            return null;
        }
    }

    private static void writeField(Field field, Object target, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            log.debug("写入字段 {} 失败: {}", field.getName(), exception.getMessage());
        }
    }

    private static boolean isSimple(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || type.isRecord()
                || Number.class.isAssignableFrom(type) || CharSequence.class.isAssignableFrom(type)
                || Boolean.class == type || Character.class == type || java.util.Date.class.isAssignableFrom(type)
                || java.time.temporal.Temporal.class.isAssignableFrom(type)
                || type.getName().startsWith("java.");
    }
}
