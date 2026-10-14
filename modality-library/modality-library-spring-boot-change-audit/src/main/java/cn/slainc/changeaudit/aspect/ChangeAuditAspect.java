package cn.slainc.changeaudit.aspect;

import cn.slainc.changeaudit.annotation.ChangeAudited;
import cn.slainc.changeaudit.api.FieldDiffEngine;
import cn.slainc.changeaudit.autoconfigure.ChangeAuditConfigReader;
import cn.slainc.changeaudit.enums.ChangeOperation;
import cn.slainc.changeaudit.enums.ChangeSource;
import cn.slainc.changeaudit.event.AfterCommitExecutor;
import cn.slainc.changeaudit.event.ImmediateAfterCommitExecutor;
import cn.slainc.changeaudit.spi.ChangeRecord;
import cn.slainc.changeaudit.spi.ChangeRecorder;
import cn.slainc.changeaudit.support.ChangeAuditExpressionEvaluator;
import cn.slainc.changeaudit.support.EntityNames;
import cn.slainc.changeaudit.support.FieldChange;
import cn.slainc.changeaudit.support.SnapshotLoaderRegistry;
import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.utils.reflect.ReflectUtils;
import cn.slainc.utils.string.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Aspect
public class ChangeAuditAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeAuditAspect.class);

    private static final int MAX_ID_LENGTH = 64;

    private static final int MAX_NAME_LENGTH = 255;

    private static final int MAX_URI_LENGTH = 512;

    private final ChangeAuditConfigReader reader;

    private final SnapshotLoaderRegistry snapshotLoaderRegistry;

    private final FieldDiffEngine fieldDiffer;

    private final ChangeRecorder changeRecorder;

    private final AfterCommitExecutor afterCommitExecutor;

    private final ChangeAuditExpressionEvaluator expressionEvaluator;

    public ChangeAuditAspect(ChangeAuditConfigReader reader, SnapshotLoaderRegistry snapshotLoaderRegistry,
                             FieldDiffEngine fieldDiffer, ChangeRecorder changeRecorder,
                             AfterCommitExecutor afterCommitExecutor) {
        this(reader, snapshotLoaderRegistry, fieldDiffer, changeRecorder, afterCommitExecutor,
                new ChangeAuditExpressionEvaluator());
    }

    public ChangeAuditAspect(ChangeAuditConfigReader reader, SnapshotLoaderRegistry snapshotLoaderRegistry,
                             FieldDiffEngine fieldDiffer, ChangeRecorder changeRecorder,
                             AfterCommitExecutor afterCommitExecutor,
                             ChangeAuditExpressionEvaluator expressionEvaluator) {
        this.reader = reader == null ? new ChangeAuditConfigReader(null, null) : reader;
        this.snapshotLoaderRegistry = snapshotLoaderRegistry;
        this.fieldDiffer = fieldDiffer;
        this.changeRecorder = changeRecorder;
        this.afterCommitExecutor = afterCommitExecutor == null
                ? new ImmediateAfterCommitExecutor() : afterCommitExecutor;
        this.expressionEvaluator = expressionEvaluator == null ? new ChangeAuditExpressionEvaluator()
                : expressionEvaluator;
    }

    @Around("@annotation(changeAudited)")
    public Object around(ProceedingJoinPoint point, ChangeAudited changeAudited) throws Throwable {
        if (!reader.enabled() || changeAudited == null || StringUtils.isBlank(changeAudited.type())) {
            return point.proceed();
        }
        Method method = methodOf(point);
        Object[] arguments = point.getArgs();
        String type = changeAudited.type().trim();
        String id = resolveId(changeAudited, method, arguments, null);
        Object before = snapshotLoaderRegistry == null ? null : snapshotLoaderRegistry.load(type, id);

        Object result = point.proceed();

        if (StringUtils.isBlank(id)) {
            id = resolveId(changeAudited, method, arguments, result);
        }
        Object after = resolveAfter(result, arguments, type, id, before);
        ChangeOperation operation = resolveOperation(changeAudited, method, before, after);
        List<FieldChange> changes = fieldDiffer == null ? List.of() : fieldDiffer.diff(before, after);
        if (changes.isEmpty() && !reader.recordNoChange()) {
            return result;
        }
        ChangeRecord change = toRecord(type, id,
                resolveName(changeAudited, method, arguments, result, before, after), operation, changes);
        dispatch(change);
        return result;
    }

    private Object resolveAfter(Object result, Object[] arguments, String type, String id, Object before) {
        Class<?> subjectType = before == null ? null : before.getClass();
        if (isEntity(result, subjectType)) {
            return result;
        }
        for (Object argument : arguments) {
            if (isEntity(argument, subjectType)) {
                return argument;
            }
        }
        return snapshotLoaderRegistry == null ? null : snapshotLoaderRegistry.load(type, id);
    }

    private static ChangeOperation resolveOperation(ChangeAudited changeAudited, Method method, Object before,
                                                    Object after) {
        if (changeAudited.operation() != null && changeAudited.operation() != ChangeOperation.AUTO) {
            return changeAudited.operation();
        }
        ChangeOperation byName = operationOfMethod(method == null ? null : method.getName());
        if (byName != null) {
            return byName;
        }
        if (before == null && after != null) {
            return ChangeOperation.INSERT;
        }
        if (before != null && after == null) {
            return ChangeOperation.DELETE;
        }
        return ChangeOperation.UPDATE;
    }

    private static ChangeOperation operationOfMethod(String methodName) {
        if (StringUtils.isBlank(methodName)) {
            return null;
        }
        String name = methodName.toLowerCase(Locale.ROOT);
        if (name.startsWith("delete") || name.startsWith("remove") || name.startsWith("purge")
                || name.startsWith("clean") || name.startsWith("clear")) {
            return ChangeOperation.DELETE;
        }
        if (name.startsWith("create") || name.startsWith("add") || name.startsWith("insert")
                || name.startsWith("save") || name.startsWith("register")) {
            return ChangeOperation.INSERT;
        }
        if (name.startsWith("update") || name.startsWith("edit") || name.startsWith("modify")
                || name.startsWith("change") || name.startsWith("set")) {
            return ChangeOperation.UPDATE;
        }
        return null;
    }

    private String resolveId(ChangeAudited changeAudited, Method method, Object[] arguments, Object result) {
        Object evaluated = expressionEvaluator.evaluate(changeAudited.id(), method, arguments, result);
        String id = scalarId(evaluated);
        if (id != null) {
            return id;
        }
        for (Object argument : arguments) {
            String candidate = entityId(argument);
            if (candidate != null) {
                return candidate;
            }
        }
        return entityId(result);
    }

    private String resolveName(ChangeAudited changeAudited, Method method, Object[] arguments, Object result,
                               Object before, Object after) {
        Object evaluated = expressionEvaluator.evaluate(changeAudited.name(), method, arguments, result);
        String name = evaluated == null ? null : String.valueOf(evaluated);
        if (StringUtils.isNotBlank(name)) {
            return truncate(name.trim(), MAX_NAME_LENGTH);
        }
        return truncate(EntityNames.of(after == null ? before : after), MAX_NAME_LENGTH);
    }

    private ChangeRecord toRecord(String type, String id, String name, ChangeOperation operation,
                                  List<FieldChange> changes) {
        RequestContext.RequestInfo request = RequestContext.get();
        return new ChangeRecord(type, truncate(id, MAX_ID_LENGTH), name, operation.name(),
                fieldDiffer == null ? "[]" : fieldDiffer.serialize(changes), changes.size(), operator(),
                UserContext.userId(), request == null ? null : truncate(request.requestUri(), MAX_URI_LENGTH),
                request == null ? null : request.traceId(),
                request == null ? ChangeSource.SYSTEM.name() : ChangeSource.API.name(), LocalDateTime.now(),
                TenantContext.get());
    }

    private void dispatch(ChangeRecord change) {
        try {
            afterCommitExecutor.afterCommit(() -> changeRecorder.record(change));
        } catch (RuntimeException e) {
            LOGGER.error("登记事务提交后的变更审计动作失败: {}", e.getMessage(), e);
        }
    }

    private static String operator() {
        String username = UserContext.username();
        if (StringUtils.isNotBlank(username)) {
            return username;
        }
        Long userId = UserContext.userId();
        return userId == null ? null : String.valueOf(userId);
    }

    private static Method methodOf(ProceedingJoinPoint point) {
        if (!(point.getSignature() instanceof MethodSignature signature)) {
            return null;
        }
        Method method = signature.getMethod();
        Object target = point.getTarget();
        if (method == null || target == null) {
            return method;
        }
        Method specific = AopUtils.getMostSpecificMethod(method, target.getClass());
        return specific == null ? method : specific;
    }

    private static String scalarId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence sequence) {
            String text = sequence.toString().trim();
            return text.isEmpty() ? null : text;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character
                || value instanceof Enum<?>) {
            return String.valueOf(value);
        }
        return null;
    }

    private static String entityId(Object value) {
        if (value == null) {
            return null;
        }
        String scalar = scalarId(value);
        if (scalar != null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                String candidate = scalarId(element);
                if (candidate == null) {
                    candidate = entityId(element);
                }
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            return entityId(List.of((Object[]) value));
        }
        Method getter = getterOf(value.getClass(), "getId");
        if (getter == null) {
            return null;
        }
        try {
            return scalarId(getter.invoke(value));
        } catch (RuntimeException | ReflectiveOperationException e) {
            // 读取失败时返回 null，由调用方按“无值”处理
            return null;
        }
    }

    private static boolean isEntity(Object value, Class<?> subjectType) {
        if (value == null || value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value instanceof Enum<?>
                || value instanceof Collection<?> || value instanceof Map<?, ?> || value instanceof Class<?>
                || value instanceof Date || value instanceof TemporalAccessor) {
            return false;
        }
        if (value.getClass().isArray() || value instanceof AutoCloseable) {
            return false;
        }
        if (subjectType != null) {
            return subjectType.isInstance(value);
        }
        Package valuePackage = value.getClass().getPackage();
        String packageName = valuePackage == null ? "" : valuePackage.getName();
        return !packageName.startsWith("java.") && !packageName.startsWith("jakarta.")
                && !Modifier.isAbstract(value.getClass().getModifiers());
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

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
