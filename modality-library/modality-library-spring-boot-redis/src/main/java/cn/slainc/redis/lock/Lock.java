package cn.slainc.redis.lock;

import cn.slainc.core.constant.CacheKeys;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {

    String key() default "";

    String prefix() default CacheKeys.LOCK;

    long waitTime() default 3L;

    long leaseTime() default 30L;

    boolean failFast() default true;

    LockType type() default LockType.REENTRANT;
}
