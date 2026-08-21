package cn.slainc.datasource.transaction;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 101)
public class TransactionLogAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionLogAspect.class);

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional)")
    public Object record(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String description = point.getTarget().getClass().getSimpleName() + '.' + signature.getName();
        long start = System.currentTimeMillis();
        try {
            Object result = point.proceed();
            log.info("事务 {} 已提交，耗时 {} 毫秒", description, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable throwable) {
            log.info("事务 {} 已回滚，耗时 {} 毫秒", description, System.currentTimeMillis() - start);
            throw throwable;
        }
    }
}
