package cn.slainc.changeaudit.annotation;

import cn.slainc.changeaudit.enums.ChangeOperation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ChangeAudited {

    String type();

    String id() default "";

    String name() default "";

    ChangeOperation operation() default ChangeOperation.AUTO;
}
