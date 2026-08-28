package cn.slainc.idempotent.aspect;

import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.idempotent.autoconfigure.IdempotentProperties;
import cn.slainc.idempotent.support.IdempotentContext;
import cn.slainc.idempotent.support.IdempotentContextFactory;
import cn.slainc.idempotent.support.IdempotentExecutor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class IdempotentAspect {

    private final IdempotentExecutor executor;

    private final IdempotentContextFactory contextFactory;

    private final IdempotentProperties properties;

    public IdempotentAspect(IdempotentExecutor executor, IdempotentContextFactory contextFactory,
                            IdempotentProperties properties) {
        if (executor == null) {
            throw new IllegalArgumentException("IdempotentExecutor 不能为空");
        }
        this.executor = executor;
        this.contextFactory = contextFactory;
        this.properties = properties;
    }

    @Around("@annotation(cn.slainc.idempotent.annotation.Idempotent) || @within(cn.slainc.idempotent.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (!properties.isEnabled()) {
            return point.proceed();
        }
        Object target = point.getTarget();
        Class<?> targetClass = target == null ? null : AopUtils.getTargetClass(target);
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);
        Idempotent annotation = AnnotatedElementUtils.findMergedAnnotation(method, Idempotent.class);
        if (annotation == null && targetClass != null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, Idempotent.class);
        }
        if (annotation == null) {
            return point.proceed();
        }
        IdempotentContext context = contextFactory.create(method, point.getArgs(), target, annotation);
        return executor.execute(context, point::proceed);
    }
}
