package cn.slainc.sensitive.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.RECORD_COMPONENT})
public @interface Sensitive {

    SensitiveType type() default SensitiveType.CUSTOM;

    SensitiveStrategy strategy() default SensitiveStrategy.MASK;

    int prefixKeep() default -1;

    int suffixKeep() default -1;

    char maskChar() default 0;

    String replacement() default "";

    String[] groups() default {};
}
