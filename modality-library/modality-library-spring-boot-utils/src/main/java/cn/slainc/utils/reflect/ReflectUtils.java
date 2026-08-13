package cn.slainc.utils.reflect;

import cn.slainc.utils.date.DateUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public final class ReflectUtils {

    private ReflectUtils() {
    }

    public static Field getField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("字段不存在: " + type.getName() + "#" + name);
    }

    public static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    public static List<Method> getAllMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && !method.isSynthetic()) {
                    methods.add(method);
                }
            }
            current = current.getSuperclass();
        }
        return methods;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            return (T) getField(target.getClass(), name).get(target);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("读取字段失败: " + name, ex);
        }
    }

    public static void setFieldValue(Object target, String name, Object value) {
        if (target == null) {
            return;
        }
        Field field = getField(target.getClass(), name);
        try {
            field.set(target, convert(value, field.getType()));
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("写入字段失败: " + name, ex);
        }
    }

    public static Type[] getGenericArguments(Field field) {
        Type type = field.getGenericType();
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments();
        }
        return new Type[0];
    }

    public static Type[] getGenericArguments(Method method) {
        Type type = method.getGenericReturnType();
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments();
        }
        return new Type[0];
    }

    public static Class<?> getRawType(Field field) {
        Type type = field.getGenericType();
        if (type instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> rawType) {
            return rawType;
        }
        return field.getType();
    }

    public static Object invokeMethod(Object target, String name, Object... args) {
        if (target == null) {
            throw new IllegalArgumentException("目标对象不能为空");
        }
        Object[] actualArgs = args == null ? new Object[0] : args;
        Method method = findMethod(target.getClass(), name, actualArgs);
        try {
            method.setAccessible(true);
            return method.invoke(target, actualArgs);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("方法调用失败: " + name, ex);
        }
    }

    private static Method findMethod(Class<?> type, String name, Object[] args) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : getAllMethods(type)) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                candidates.add(method);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("方法不存在: " + type.getName() + "#" + name);
        }
        for (Method candidate : candidates) {
            if (isAssignable(candidate.getParameterTypes(), args)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private static boolean isAssignable(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            if (!box(parameterTypes[i]).isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    public static <A extends Annotation> A getAnnotation(Class<?> type, Class<A> annotationType) {
        return type == null ? null : type.getAnnotation(annotationType);
    }

    public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationType) {
        return method == null ? null : method.getAnnotation(annotationType);
    }

    public static <A extends Annotation> A getFieldAnnotation(Class<?> type, String fieldName, Class<A> annotationType) {
        if (type == null) {
            return null;
        }
        try {
            return getField(type, fieldName).getAnnotation(annotationType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("实例化失败: " + type.getName(), ex);
        }
    }

    public static Object newInnerInstance(Class<?> type, Object outerInstance, Object... args) {
        try {
            Class<?>[] parameterTypes = new Class<?>[args.length + 1];
            Object[] actualArgs = new Object[args.length + 1];
            parameterTypes[0] = outerInstance.getClass();
            actualArgs[0] = outerInstance;
            for (int i = 0; i < args.length; i++) {
                parameterTypes[i + 1] = args[i] == null ? Object.class : args[i].getClass();
                actualArgs[i + 1] = args[i];
            }
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(actualArgs);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("内部类实例化失败: " + type.getName(), ex);
        }
    }

    public static boolean isProxy(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (java.lang.reflect.Proxy.isProxyClass(type)) {
            return true;
        }
        String name = type.getName();
        return name.contains("$$") || name.contains("$Proxy");
    }

    public static Class<?> getTargetClass(Object candidate) {
        if (candidate == null) {
            return null;
        }
        Class<?> type = candidate.getClass();
        if (!isProxy(type)) {
            return type;
        }
        Class<?> superclass = type.getSuperclass();
        return superclass == null || superclass == Object.class ? type : superclass;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T convert(Object value, Class<T> targetType) {
        if (value == null || targetType == null) {
            return null;
        }
        Class<?> boxed = box(targetType);
        if (boxed.isInstance(value)) {
            return (T) value;
        }
        if (boxed == String.class) {
            return (T) String.valueOf(value);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (boxed == Integer.class) {
            return (T) Integer.valueOf(text);
        }
        if (boxed == Long.class) {
            return (T) Long.valueOf(text);
        }
        if (boxed == Double.class) {
            return (T) Double.valueOf(text);
        }
        if (boxed == Float.class) {
            return (T) Float.valueOf(text);
        }
        if (boxed == Short.class) {
            return (T) Short.valueOf(text);
        }
        if (boxed == Byte.class) {
            return (T) Byte.valueOf(text);
        }
        if (boxed == Boolean.class) {
            return (T) Boolean.valueOf(text);
        }
        if (boxed == BigDecimal.class) {
            return (T) new BigDecimal(text);
        }
        if (boxed == BigInteger.class) {
            return (T) new BigInteger(text);
        }
        if (boxed == Character.class) {
            return (T) Character.valueOf(text.charAt(0));
        }
        if (boxed.isEnum()) {
            return (T) Enum.valueOf((Class<? extends Enum>) boxed.asSubclass(Enum.class), text);
        }
        if (boxed == LocalDate.class) {
            return (T) DateUtils.parseDate(text);
        }
        if (boxed == LocalDateTime.class) {
            return (T) DateUtils.parseDateTime(text);
        }
        if (boxed == Date.class) {
            LocalDateTime parsed = DateUtils.parseDateTime(text);
            return (T) (parsed == null ? null : DateUtils.toDate(parsed));
        }
        if (value instanceof Number number) {
            return convert(number, targetType);
        }
        throw new IllegalArgumentException("无法将 " + value.getClass().getName() + " 转换为 " + targetType.getName());
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    public static List<Class<?>> getInterfaces(Class<?> type) {
        return type == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(type.getInterfaces()));
    }
}
