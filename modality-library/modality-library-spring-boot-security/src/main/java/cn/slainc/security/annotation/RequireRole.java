package cn.slainc.security.annotation;

import cn.slainc.core.enums.LoginType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    LoginType value() default LoginType.USER;

    String[] roles();

    CheckMode mode() default CheckMode.ALL;
}
