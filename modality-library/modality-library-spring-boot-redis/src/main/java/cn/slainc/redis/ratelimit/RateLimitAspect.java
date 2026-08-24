package cn.slainc.redis.ratelimit;

import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.UserContext;
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
import java.time.Duration;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimiter rateLimiter;

    private final RedisProperties properties;

    public RateLimitAspect(RateLimiter rateLimiter, RedisProperties properties) {
        if (rateLimiter == null) {
            throw new IllegalArgumentException("RateLimiter 不能为空");
        }
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Around("@annotation(cn.slainc.redis.ratelimit.RateLimit) || @within(cn.slainc.redis.ratelimit.RateLimit)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        if (annotation == null) {
            annotation = point.getTarget().getClass().getAnnotation(RateLimit.class);
        }
        if (annotation == null) {
            return point.proceed();
        }
        String key = resolveKey(annotation, method, point);
        long limit = annotation.limit() > 0L ? annotation.limit() : properties.getRateLimit().getDefaultLimit();
        Duration window = annotation.windowSeconds() > 0L
                ? Duration.ofSeconds(annotation.windowSeconds())
                : properties.getRateLimit().getDefaultWindow();
        boolean allowed;
        try {
            allowed = rateLimiter.tryAcquire(key, annotation.type(), limit, window);
        } catch (Exception exception) {
            if (annotation.failOpen()) {
                log.warn("限流校验异常已放行，键={}，原因={}", key, exception.getMessage());
                return point.proceed();
            }
            throw exception;
        }
        if (!allowed) {
            throw new RateLimitExceededException(annotation.message());
        }
        return point.proceed();
    }

    private String resolveKey(RateLimit annotation, Method method, ProceedingJoinPoint point) {
        String dimension = switch (annotation.dimension()) {
            case IP -> nullTo(RequestContext.clientIp(), "未知IP");
            case USER -> {
                Long userId = UserContext.userId();
                yield userId == null ? "匿名用户" : String.valueOf(userId);
            }
            case CUSTOM -> RedisSpelResolver.resolveOrDefault(annotation.key(), method, point.getArgs(),
                    point.getTarget(), method.getName());
            case GLOBAL -> "全局";
            default -> method.getDeclaringClass().getSimpleName() + "#" + method.getName();
        };
        if (annotation.dimension() == RateLimitDimension.CUSTOM) {
            return dimension;
        }
        String prefix = annotation.key().isBlank()
                ? method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                : RedisSpelResolver.resolveOrDefault(annotation.key(), method, point.getArgs(), point.getTarget(),
                        method.getName());
        return prefix + ":" + dimension;
    }

    private static String nullTo(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
