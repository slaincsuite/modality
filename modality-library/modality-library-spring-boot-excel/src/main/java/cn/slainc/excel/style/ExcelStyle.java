package cn.slainc.excel.style;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelStyle {

    String fontName() default "";

    int fontSize() default -1;

    boolean bold() default false;

    boolean italic() default false;

    boolean underline() default false;

    boolean strikeout() default false;

    String fontColor() default "";

    String foregroundColor() default "";

    String backgroundColor() default "";

    String borderColor() default "";

    BorderWeight border() default BorderWeight.NONE;

    CellAlign horizontal() default CellAlign.GENERAL;

    VerticalAlign vertical() default VerticalAlign.GENERAL;

    boolean wrapText() default false;

    int indent() default 0;

    int rotation() default 0;

    NumberPattern numberPattern() default NumberPattern.GENERAL;

    String dataFormat() default "";
}
