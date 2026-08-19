package cn.slainc.excel.formula;

public record Formula(String expression, Object cachedValue) {

    public Formula {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("公式表达式不能为空");
        }
        expression = expression.startsWith("=") ? expression.substring(1) : expression;
    }

    public static Formula of(String expression) {
        return new Formula(expression, null);
    }

    public static Formula of(String expression, Object cachedValue) {
        return new Formula(expression, cachedValue);
    }
}
