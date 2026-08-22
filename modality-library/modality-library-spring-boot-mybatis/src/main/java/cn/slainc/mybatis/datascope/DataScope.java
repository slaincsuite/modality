package cn.slainc.mybatis.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DataScope {

    String userColumn() default "created_by";

    String deptColumn() default "dept_id";

    boolean userScope() default true;

    boolean deptScope() default false;

    String alias() default "";
}
