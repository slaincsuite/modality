package cn.slainc.mybatis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sensitive {

    SensitiveType type() default SensitiveType.DEFAULT;

    int prefixKeep() default 0;

    int suffixKeep() default 0;

    String mask() default "*";
}
