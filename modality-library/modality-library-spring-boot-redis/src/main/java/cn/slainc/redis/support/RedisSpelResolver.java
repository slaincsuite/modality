package cn.slainc.redis.support;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

public final class RedisSpelResolver {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private RedisSpelResolver() {
    }

    public static String resolve(String expression, Method method, Object[] args, Object target) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        EvaluationContext context;
        if (method == null) {
            context = new StandardEvaluationContext(target);
        } else {
            context = new MethodBasedEvaluationContext(target, method, args, NAME_DISCOVERER);
        }
        Object value = PARSER.parseExpression(expression).getValue(context);
        return value == null ? "" : String.valueOf(value);
    }

    public static String resolveOrDefault(String expression, Method method, Object[] args, Object target,
                                          String defaultValue) {
        String value = resolve(expression, method, args, target);
        return value.isBlank() ? defaultValue : value;
    }
}
