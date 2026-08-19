package cn.slainc.excel.style;

public final class ConditionalFormatSpec {

    public enum Operator {

        GREATER_THAN("大于"),
        LESS_THAN("小于"),
        EQUAL("等于"),
        NOT_EQUAL("不等于"),
        BETWEEN("介于");

        private final String description;

        Operator(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private final String range;
    private final Operator operator;
    private final String value1;
    private final String value2;
    private final String fillColor;
    private final String fontColor;
    private final boolean bold;

    private ConditionalFormatSpec(Builder builder) {
        this.range = builder.range;
        this.operator = builder.operator;
        this.value1 = builder.value1;
        this.value2 = builder.value2;
        this.fillColor = builder.fillColor;
        this.fontColor = builder.fontColor;
        this.bold = builder.bold;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ConditionalFormatSpec greaterThan(String range, String value) {
        return builder().range(range).operator(Operator.GREATER_THAN).value1(value).fillColor("#FFC7CE")
                .fontColor("#9C0006").build();
    }

    public static ConditionalFormatSpec lessThan(String range, String value) {
        return builder().range(range).operator(Operator.LESS_THAN).value1(value).fillColor("#FFEB9C")
                .fontColor("#9C6500").build();
    }

    public static ConditionalFormatSpec between(String range, String min, String max) {
        return builder().range(range).operator(Operator.BETWEEN).value1(min).value2(max).fillColor("#C6EFCE")
                .fontColor("#006100").build();
    }

    public String getRange() {
        return range;
    }

    public Operator getOperator() {
        return operator;
    }

    public String getValue1() {
        return value1;
    }

    public String getValue2() {
        return value2;
    }

    public String getFillColor() {
        return fillColor;
    }

    public String getFontColor() {
        return fontColor;
    }

    public boolean isBold() {
        return bold;
    }

    public static final class Builder {

        private String range;
        private Operator operator = Operator.GREATER_THAN;
        private String value1;
        private String value2;
        private String fillColor;
        private String fontColor;
        private boolean bold;

        public Builder range(String value) {
            this.range = value;
            return this;
        }

        public Builder operator(Operator value) {
            this.operator = value;
            return this;
        }

        public Builder value1(String value) {
            this.value1 = value;
            return this;
        }

        public Builder value2(String value) {
            this.value2 = value;
            return this;
        }

        public Builder fillColor(String value) {
            this.fillColor = value;
            return this;
        }

        public Builder fontColor(String value) {
            this.fontColor = value;
            return this;
        }

        public Builder bold(boolean value) {
            this.bold = value;
            return this;
        }

        public ConditionalFormatSpec build() {
            if (range == null || range.isBlank()) {
                throw new IllegalArgumentException("条件格式的单元格范围不能为空");
            }
            if (value1 == null) {
                throw new IllegalArgumentException("条件格式的比较值不能为空");
            }
            if (operator == Operator.BETWEEN && value2 == null) {
                throw new IllegalArgumentException("介于比较必须提供上下界");
            }
            return new ConditionalFormatSpec(this);
        }
    }
}
