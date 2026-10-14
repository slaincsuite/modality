package cn.slainc.logger.oplog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    String value() default "";

    AuditOperation operation() default AuditOperation.OTHER;

    AuditCategory category() default AuditCategory.DATA;

    AuditLevel level() default AuditLevel.INFO;

    String module() default "";

    boolean recordParams() default true;

    boolean recordResult() default false;

    boolean recordException() default true;

    boolean recordCost() default true;
}
