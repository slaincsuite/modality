package cn.slainc.redis.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    String key() default "";

    long limit() default 0L;

    long windowSeconds() default 0L;

    RateLimitType type() default RateLimitType.TOKEN_BUCKET;

    RateLimitDimension dimension() default RateLimitDimension.API;

    String message() default "请求过于频繁，请稍后再试";

    boolean failOpen() default true;
}
