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

    private final RoutingDataSource routingDataSource;

    private final String fallbackPrimary;

    public TransactionMasterAspect(String primaryName) {
        this(null, primaryName);
    }

    public TransactionMasterAspect(RoutingDataSource routingDataSource, String fallbackPrimary) {
        this.routingDataSource = routingDataSource;
        this.fallbackPrimary = fallbackPrimary;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional)")
    public Object forceMaster(ProceedingJoinPoint point) throws Throwable {
        if (hasExplicitDataSource(point)) {
            return point.proceed();
        }
        DynamicDataSourceContextHolder.push(currentPrimary());
        try {
            return point.proceed();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    // 主库在运行期会被故障切换改写，固化启动时的名字会把事务压回已经故障的旧主库
    private String currentPrimary() {
        String name = routingDataSource == null ? null : routingDataSource.primaryName();
        return name == null || name.isBlank() ? fallbackPrimary : name;
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
