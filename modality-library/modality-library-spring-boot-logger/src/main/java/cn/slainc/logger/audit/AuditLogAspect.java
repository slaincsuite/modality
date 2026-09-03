package cn.slainc.logger.audit;

import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.constant.LogMdcKeys;
import cn.slainc.logger.context.LoginIdResolver;
import cn.slainc.logger.mask.LogMasker;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.utils.random.UuidUtils;
import cn.slainc.web.log.SensitiveMasker;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Aspect
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final LoggerProperties.Audit properties;

    private final AuditLogDispatcher dispatcher;

    private final LogMasker masker;

    private final List<AuditSnapshotProvider> snapshotProviders;

    private final LoginIdResolver loginIdResolver;

    public AuditLogAspect(LoggerProperties.Audit properties, AuditLogDispatcher dispatcher, LogMasker masker,
                          List<AuditSnapshotProvider> snapshotProviders, LoginIdResolver loginIdResolver) {
        this.properties = properties;
        this.dispatcher = dispatcher;
        this.masker = masker;
        List<AuditSnapshotProvider> sorted = snapshotProviders == null
                ? new ArrayList<>() : new ArrayList<>(snapshotProviders);
        sorted.sort((left, right) -> Integer.compare(left.order(), right.order()));
        this.snapshotProviders = List.copyOf(sorted);
        this.loginIdResolver = loginIdResolver;
    }

    @Around("@annotation(cn.slainc.logger.audit.AuditLog) || @within(cn.slainc.logger.audit.AuditLog)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        AuditLog annotation = method.getAnnotation(AuditLog.class);
        if (annotation == null && point.getTarget() != null) {
            annotation = point.getTarget().getClass().getAnnotation(AuditLog.class);
        }
        if (annotation == null) {
            return point.proceed();
        }
        AuditCategory category = resolveCategory(annotation);
        String previousCategory = MDC.get(LogMdcKeys.AUDIT_CATEGORY);
        String previousLevel = MDC.get(LogMdcKeys.AUDIT_LEVEL);
        MDC.put(LogMdcKeys.AUDIT_CATEGORY, category.name());
        MDC.put(LogMdcKeys.AUDIT_LEVEL, annotation.level().name());
        long start = System.nanoTime();
        Object before = snapshot(point, method, true, null);
        try {
            Object result = point.proceed();
            publish(annotation, category, method, signature, point, result, null, before, elapsed(start));
            return result;
        } catch (Throwable ex) {
            publish(annotation, category, method, signature, point, null, ex, before, elapsed(start));
            throw ex;
        } finally {
            restore(LogMdcKeys.AUDIT_CATEGORY, previousCategory);
            restore(LogMdcKeys.AUDIT_LEVEL, previousLevel);
        }
    }

    private void publish(AuditLog annotation, AuditCategory category, Method method, MethodSignature signature,
                         ProceedingJoinPoint point, Object result, Throwable error, Object before, long costMillis) {
        try {
            boolean failed = error != null;
            AuditLevel level = failed && annotation.recordException() ? AuditLevel.ERROR : annotation.level();
            String description = annotation.value() == null || annotation.value().isBlank()
                    ? method.getName() : annotation.value();
            String module = annotation.module() == null || annotation.module().isBlank()
                    ? method.getDeclaringClass().getSimpleName() : annotation.module();
            Class<?> targetClass = point.getTarget() == null ? method.getDeclaringClass() : point.getTarget().getClass();
            RequestContext.RequestInfo requestInfo = RequestContext.get();
            AuditRecord record = new AuditRecord(
                    UuidUtils.uuidV7Compact(),
                    Instant.now(),
                    description,
                    module,
                    annotation.operation(),
                    category,
                    level,
                    !failed,
                    UserContext.userId(),
                    loginIdResolver == null ? null : loginIdResolver.resolveLoginId(),
                    UserContext.username(),
                    TenantContext.get(),
                    RequestContext.clientIp(),
                    RequestContext.userAgent(),
                    MDC.get(LogMdcKeys.DEVICE_ID),
                    requestInfo == null ? null : requestInfo.requestUri(),
                    requestInfo == null ? null : requestInfo.method(),
                    RequestContext.traceId(),
                    MDC.get(LogMdcKeys.SPAN_ID),
                    MDC.get(LogMdcKeys.PARENT_SPAN_ID),
                    targetClass.getName() + "#" + method.getName(),
                    annotation.recordParams() ? serializeParams(point, signature) : null,
                    annotation.recordResult() && !failed ? serialize(result) : null,
                    properties.isRecordBeforeSnapshot() ? serialize(before) : null,
                    failed ? null : serialize(snapshot(point, method, false, result)),
                    failed ? error.getClass().getName() : null,
                    failed ? truncate(error.getMessage()) : null,
                    annotation.recordCost() ? costMillis : -1L);
            dispatcher.dispatch(record);
        } catch (RuntimeException ex) {
            log.warn("审计日志采集失败：{}#{}", method.getDeclaringClass().getName(), method.getName(), ex);
        }
    }

    private AuditCategory resolveCategory(AuditLog annotation) {
        if (annotation.category() != AuditCategory.DATA) {
            return annotation.category();
        }
        return switch (annotation.operation()) {
            case LOGIN, LOGOUT -> AuditCategory.LOGIN;
            case GRANT, REVOKE -> AuditCategory.PERMISSION;
            case CONFIG -> AuditCategory.SYSTEM;
            default -> AuditCategory.DATA;
        };
    }

    private Object snapshot(ProceedingJoinPoint point, Method method, boolean before, Object result) {
        if (snapshotProviders.isEmpty()) {
            return null;
        }
        for (AuditSnapshotProvider provider : snapshotProviders) {
            try {
                if (!provider.supports(method)) {
                    continue;
                }
                Object snapshot = before
                        ? provider.before(point.getTarget(), method, point.getArgs())
                        : provider.after(point.getTarget(), method, point.getArgs(), result);
                if (snapshot != null) {
                    return snapshot;
                }
            } catch (RuntimeException ex) {
                log.warn("审计快照采集失败：{}", provider.getClass().getName(), ex);
            }
        }
        return null;
    }

    // 参数按 -parameters 保留的真实形参名落成字段，才能对 phone / password 这类敏感字段按名脱敏
    private String serializeParams(ProceedingJoinPoint point, MethodSignature signature) {
        Object[] args = point.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        String[] names = signature.getParameterNames();
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String name = names != null && i < names.length && names[i] != null ? names[i] : "arg" + i;
            values.put(name, normalize(args[i]));
        }
        return serialize(values);
    }

    private Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?>) {
            return value;
        }
        try {
            String json = JsonUtils.toJson(value);
            return json == null ? String.valueOf(value) : JsonUtils.mapper().readValue(json, Object.class);
        } catch (RuntimeException ex) {
            return String.valueOf(value);
        }
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        Object normalized = normalize(value);
        String text;
        try {
            text = JsonUtils.toJson(normalized);
        } catch (RuntimeException ex) {
            text = String.valueOf(value);
        }
        if (text == null) {
            text = String.valueOf(value);
        }
        if (masker != null) {
            text = masker.maskMessage(text);
        }
        return truncate(text);
    }

    private String truncate(String text) {
        return SensitiveMasker.truncate(text, properties.getMaxPayloadLength());
    }

    private long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private void restore(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
