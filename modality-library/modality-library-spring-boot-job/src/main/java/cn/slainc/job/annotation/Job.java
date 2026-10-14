package cn.slainc.job.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 声明式任务注解：标注在 JobHandler 实现类上，内核启动时自动注册
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Job {

    String name();

    String cron() default "";

    String description() default "";

    int retryCount() default 0;

    boolean enabled() default true;
}
