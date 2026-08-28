package cn.slainc.idempotent.support;

import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.autoconfigure.IdempotentProperties;
import cn.slainc.redis.support.RedisSpelResolver;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Locale;

public class IdempotentContextFactory {

    private static final Logger log = LoggerFactory.getLogger(IdempotentContextFactory.class);

    private static final List<String> TOKEN_NAMES = List.of("token", "idempotenttoken", "idempotentkey", "requesttoken");

    private final IdempotentProperties properties;

    public IdempotentContextFactory(IdempotentProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("IdempotentProperties 不能为空");
        }
        this.properties = properties;
    }

    public IdempotentContext create(Method method, Object[] args, Object target, Idempotent annotation) {
        String methodId = method.getDeclaringClass().getName() + "#" + method.getName();
        String keyValue = resolveKeyValue(annotation, method, args, target);
        String token = resolveToken(annotation, method, args, target, keyValue);
        return new IdempotentContext(method, args, target, annotation, methodId, requestPath(), UserContext.userId(),
                keyValue, token, null, null);
    }

    private String resolveKeyValue(Idempotent annotation, Method method, Object[] args, Object target) {
        if (StringUtils.isBlank(annotation.key())) {
            return null;
        }
        try {
            String value = RedisSpelResolver.resolve(annotation.key(), method, args, target);
            return StringUtils.isBlank(value) ? null : value;
        } catch (Exception exception) {
            log.warn("幂等键 SpEL 表达式 {} 解析失败，原因={}", annotation.key(), exception.getMessage());
            return null;
        }
    }

    private String resolveToken(Idempotent annotation, Method method, Object[] args, Object target, String keyValue) {
        IdempotentStrategy strategy = annotation.strategy();
        if (strategy != IdempotentStrategy.TOKEN && strategy != IdempotentStrategy.HYBRID) {
            return null;
        }
        if (StringUtils.isNotBlank(keyValue)) {
            return keyValue;
        }
        String byName = findByParameterName(method, args);
        if (byName != null) {
            return byName;
        }
        String byField = findByField(args);
        return byField != null ? byField : fromHeader();
    }

    private static String findByParameterName(Method method, Object[] args) {
        if (method == null || args == null) {
            return null;
        }
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            if (!(args[i] instanceof CharSequence text)) {
                continue;
            }
            String name = parameters[i].getName().toLowerCase(Locale.ROOT);
            if (TOKEN_NAMES.contains(name) && StringUtils.isNotBlank(text)) {
                return text.toString().trim();
            }
        }
        return null;
    }

    private static String findByField(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg == null || arg instanceof CharSequence) {
                continue;
            }
            String value = readAccessor(arg);
            if (value != null) {
                return value;
            }
            for (Field field : arg.getClass().getDeclaredFields()) {
                if (!TOKEN_NAMES.contains(field.getName().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(arg);
                    if (fieldValue instanceof CharSequence text && StringUtils.isNotBlank(text)) {
                        return text.toString().trim();
                    }
                } catch (Exception ignored) {
                    // 反射读取失败时继续尝试其它取值方式
                }
            }
        }
        return null;
    }

    private static String readAccessor(Object arg) {
        for (Method accessor : arg.getClass().getMethods()) {
            if (accessor.getParameterCount() != 0) {
                continue;
            }
            String name = accessor.getName().toLowerCase(Locale.ROOT);
            if (!name.startsWith("get") || !TOKEN_NAMES.contains(name.substring(3))) {
                continue;
            }
            try {
                Object value = accessor.invoke(arg);
                if (value instanceof CharSequence text && StringUtils.isNotBlank(text)) {
                    return text.toString().trim();
                }
            } catch (Exception ignored) {
                // 反射调用失败时继续尝试其它取值方式
            }
        }
        return null;
    }

    private String fromHeader() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                String value = servletAttributes.getRequest().getHeader(properties.getToken().getHeaderName());
                return StringUtils.isBlank(value) ? null : value.trim();
            }
        } catch (Exception exception) {
            log.warn("从请求头读取幂等 Token 失败，原因={}", exception.getMessage());
        }
        return null;
    }

    private static String requestPath() {
        RequestContext.RequestInfo info = RequestContext.get();
        if (info == null || StringUtils.isBlank(info.requestUri())) {
            return null;
        }
        return StringUtils.isBlank(info.method()) ? info.requestUri() : info.method() + " " + info.requestUri();
    }
}
