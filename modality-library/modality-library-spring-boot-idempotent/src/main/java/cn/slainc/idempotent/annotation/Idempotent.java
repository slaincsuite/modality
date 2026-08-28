package cn.slainc.idempotent.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    String key() default "";

    long ttl() default 0L;

    String message() default "";

    IdempotentStrategy strategy() default IdempotentStrategy.PARAMETER;

    String handler() default "";

    String[] ignoreFields() default {};

    int maxRetries() default -1;

    long retryWindow() default 0L;
}
