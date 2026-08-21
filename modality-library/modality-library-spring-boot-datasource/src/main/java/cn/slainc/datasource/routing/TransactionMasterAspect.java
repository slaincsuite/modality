package cn.slainc.datasource.routing;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class TransactionMasterAspect {

    private final String primaryName;

    public TransactionMasterAspect(String primaryName) {
        this.primaryName = primaryName;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional)")
    public Object forceMaster(ProceedingJoinPoint point) throws Throwable {
        if (hasExplicitDataSource(point)) {
            return point.proceed();
        }
        DynamicDataSourceContextHolder.push(primaryName);
        try {
            return point.proceed();
        } finally {
            DynamicDataSourceContextHolder.poll();
            MasterForceHolder.clear();
        }
    }

    private boolean hasExplicitDataSource(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        if (AnnotationUtils.findAnnotation(signature.getMethod(), DS.class) != null) {
            return true;
        }
        Class<?> declaring = signature.getMethod().getDeclaringClass();
        if (AnnotationUtils.findAnnotation(declaring, DS.class) != null) {
            return true;
        }
        return AnnotationUtils.findAnnotation(point.getTarget().getClass(), DS.class) != null;
    }
}
