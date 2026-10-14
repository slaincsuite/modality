package cn.slainc.changeaudit.support;

import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChangeAuditExpressionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeAuditExpressionEvaluator.class);

    private static final String RESULT_VARIABLE = "result";

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public Object evaluate(String expression, Method method, Object[] arguments, Object result) {
        if (StringUtils.isBlank(expression) || method == null) {
            return null;
        }
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(null, method,
                    arguments == null ? new Object[0] : arguments, parameterNameDiscoverer);
            context.setVariable(RESULT_VARIABLE, result);
            return expressionParser.parseExpression(expression).getValue(context);
        } catch (RuntimeException e) {
            if (warned.add(method.getName() + '#' + expression) && LOGGER.isWarnEnabled()) {
                LOGGER.warn("求值变更审计表达式 {} 于方法 {} 失败，按无值处理: {}", expression, method.getName(),
                        e.getMessage());
            }
            return null;
        }
    }
}
