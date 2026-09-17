package cn.slainc.policy.model;

public enum ConditionOperator {

    STRING_EQUAL("string_equal", Category.STRING),
    STRING_NOT_EQUAL("string_not_equal", Category.STRING),
    STRING_LIKE("string_like", Category.STRING),
    NUMERIC_EQUAL("numeric_equal", Category.NUMERIC),
    NUMERIC_LESS_THAN("numeric_less_than", Category.NUMERIC),
    NUMERIC_GREATER_THAN("numeric_greater_than", Category.NUMERIC),
    DATE_LESS_THAN("date_less_than", Category.DATE),
    DATE_GREATER_THAN("date_greater_than", Category.DATE),
    BOOL_EQUAL("bool_equal", Category.BOOLEAN),
    IP_EQUAL("ip_equal", Category.IP),
    IP_IN_RANGE("ip_in_range", Category.IP),
    NULL_EQUAL("null_equal", Category.NULL);

    private final String code;

    private final Category category;

    ConditionOperator(String code, Category category) {
        this.code = code;
        this.category = category;
    }

    public String code() {
        return code;
    }

    public Category category() {
        return category;
    }

    public static ConditionOperator of(String code) {
        for (ConditionOperator operator : values()) {
            if (operator.code.equals(code)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("非法的条件运算符: " + code + "，支持 " + supportedCodes());
    }

    public static String supportedCodes() {
        StringBuilder builder = new StringBuilder();
        for (ConditionOperator operator : values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(operator.code);
        }
        return builder.toString();
    }

    public enum Category {
        STRING,
        NUMERIC,
        DATE,
        BOOLEAN,
        IP,
        NULL
    }
}
