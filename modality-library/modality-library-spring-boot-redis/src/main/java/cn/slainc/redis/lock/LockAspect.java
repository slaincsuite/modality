package cn.slainc.redis.lock;

import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.support.RedisSpelResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LockAspect {

    private static final Logger log = LoggerFactory.getLogger(LockAspect.class);

    private final DistributedLockTemplate lockTemplate;

    private final RedisProperties properties;

    public LockAspect(DistributedLockTemplate lockTemplate, RedisProperties properties) {
        if (lockTemplate == null) {
            throw new IllegalArgumentException("DistributedLockTemplate 不能为空");
        }
        this.lockTemplate = lockTemplate;
        this.properties = properties;
    }

    @Around("@annotation(cn.slainc.redis.lock.Lock) || @within(cn.slainc.redis.lock.Lock)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Lock annotation = method.getAnnotation(Lock.class);
        if (annotation == null) {
            annotation = point.getTarget().getClass().getAnnotation(Lock.class);
        }
        if (annotation == null) {
            return point.proceed();
        }
        String resolved = RedisSpelResolver.resolveOrDefault(annotation.key(), method, point.getArgs(),
                point.getTarget(), method.getDeclaringClass().getSimpleName() + "#" + method.getName());
        String lockKey = annotation.prefix() + resolved;
        long waitTime = annotation.waitTime();
        long leaseTime = annotation.leaseTime();
        try (LockHandle handle = lockTemplate.acquire(lockKey, waitTime, leaseTime, annotation.type())) {
            if (!handle.isLocked()) {
                if (annotation.failFast()) {
                    throw new LockAcquireException("获取分布式锁失败：" + lockKey);
                }
                log.warn("获取分布式锁失败已降级直接执行，键={}", lockKey);
            }
            return point.proceed();
        }
    }
}
