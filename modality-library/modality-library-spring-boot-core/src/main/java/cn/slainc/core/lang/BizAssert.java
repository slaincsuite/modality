package cn.slainc.core.lang;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ErrorCode;
import cn.slainc.core.result.ResultCode;

import java.util.Collection;
import java.util.Map;

public final class BizAssert {

    private BizAssert() {
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BizException(message);
        }
    }

    public static void isTrue(boolean expression, ErrorCode errorCode, String message) {
        if (!expression) {
            throw new BizException(errorCode, message);
        }
    }

    public static void isFalse(boolean expression, String message) {
        isTrue(!expression, message);
    }

    public static <T> T notNull(T value, String message) {
        if (value == null) {
            throw new BizException(ResultCode.PARAM_MISSING, message);
        }
        return value;
    }

    public static String hasText(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, message);
        }
        return text;
    }

    public static <T extends Collection<?>> T notEmpty(T collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, message);
        }
        return collection;
    }

    public static <K, V> Map<K, V> notEmpty(Map<K, V> map, String message) {
        if (map == null || map.isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, message);
        }
        return map;
    }

    public static void found(boolean exists, String message) {
        if (!exists) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, message);
        }
    }

    public static void notFound(boolean exists, String message) {
        if (exists) {
            throw new BizException(ResultCode.DATA_DUPLICATE, message);
        }
    }

    public static void state(boolean valid, String message) {
        if (!valid) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, message);
        }
    }
}
