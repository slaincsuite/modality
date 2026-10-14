package cn.slainc.recycle.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.reflect.ReflectUtils;
import cn.slainc.utils.string.StringUtils;

import java.lang.reflect.Field;

public final class EntityIds {

    private static final String ID_FIELD = "id";

    private EntityIds() {
    }

    public static Class<?> idType(Class<?> entityClass) {
        if (entityClass == null) {
            return String.class;
        }
        try {
            Field field = ReflectUtils.getField(entityClass, ID_FIELD);
            return field.getType();
        } catch (IllegalArgumentException e) {
            return String.class;
        }
    }

    public static Object parse(Class<?> entityClass, String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new BizException(ResultCode.BAD_REQUEST, "记录标识不能为空");
        }
        String value = raw.trim();
        Class<?> idType = idType(entityClass);
        try {
            if (idType == Long.class || idType == long.class) {
                return Long.valueOf(value);
            }
            if (idType == Integer.class || idType == int.class) {
                return Integer.valueOf(value);
            }
            if (idType == Short.class || idType == short.class) {
                return Short.valueOf(value);
            }
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法的记录标识: " + value);
        }
        return value;
    }

    public static String stringify(Object id) {
        return id == null ? null : String.valueOf(id);
    }
}
