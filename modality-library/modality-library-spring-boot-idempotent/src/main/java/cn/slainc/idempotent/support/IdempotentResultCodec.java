package cn.slainc.idempotent.support;

import cn.slainc.idempotent.store.IdempotentRecord;
import cn.slainc.idempotent.store.IdempotentState;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class IdempotentResultCodec {

    private static final Logger log = LoggerFactory.getLogger(IdempotentResultCodec.class);

    public IdempotentRecord encode(Object result, Method method) {
        long now = System.currentTimeMillis();
        if (result == null) {
            return new IdempotentRecord(IdempotentState.SUCCESS, null, null, now);
        }
        try {
            String payload = JsonUtils.toJson(result);
            return new IdempotentRecord(IdempotentState.SUCCESS, result.getClass().getName(), payload, now);
        } catch (Exception exception) {
            log.warn("幂等结果序列化失败，本次不缓存首次结果，方法={}，原因={}", methodName(method), exception.getMessage());
            return new IdempotentRecord(IdempotentState.SUCCESS, null, null, now);
        }
    }

    public Object decode(IdempotentRecord record, Method method) {
        if (record == null || !record.hasPayload()) {
            return null;
        }
        JavaType type = resolveType(record, method);
        if (type == null) {
            return null;
        }
        try {
            return JsonUtils.mapper().readValue(record.payload(), type);
        } catch (Exception exception) {
            log.warn("幂等结果反序列化失败，方法={}，类型={}，原因={}", methodName(method), record.className(),
                    exception.getMessage());
            return null;
        }
    }

    private JavaType resolveType(IdempotentRecord record, Method method) {
        ObjectMapper mapper = JsonUtils.mapper();
        Type declared = method == null ? null : method.getGenericReturnType();
        if (declared != null) {
            try {
                JavaType type = mapper.constructType(declared);
                if (record.className() == null || record.className().equals(type.getRawClass().getName())) {
                    return type;
                }
            } catch (Exception exception) {
                log.warn("解析方法返回类型失败，方法={}，原因={}", methodName(method), exception.getMessage());
            }
        }
        if (record.className() == null) {
            return null;
        }
        try {
            ClassLoader classLoader = method == null
                    ? IdempotentResultCodec.class.getClassLoader() : method.getDeclaringClass().getClassLoader();
            return mapper.constructType(Class.forName(record.className(), false, classLoader));
        } catch (ClassNotFoundException exception) {
            log.warn("幂等结果类型 {} 已不存在，无法还原首次结果", record.className());
            return null;
        }
    }

    private static String methodName(Method method) {
        return method == null ? "未知方法" : method.getDeclaringClass().getName() + "#" + method.getName();
    }
}
