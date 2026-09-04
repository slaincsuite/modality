package cn.slainc.monitor.alert;

public enum AlertOperator {

    GREATER_THAN("大于") {
        @Override
        public boolean test(double value, double threshold) {
            return value > threshold;
        }
    },
    GREATER_OR_EQUAL("大于等于") {
        @Override
        public boolean test(double value, double threshold) {
            return value >= threshold;
        }
    },
    LESS_THAN("小于") {
        @Override
        public boolean test(double value, double threshold) {
            return value < threshold;
        }
    },
    LESS_OR_EQUAL("小于等于") {
        @Override
        public boolean test(double value, double threshold) {
            return value <= threshold;
        }
    },
    EQUAL("等于") {
        @Override
        public boolean test(double value, double threshold) {
            return Math.abs(value - threshold) < 1.0E-9;
        }
    },
    NOT_EQUAL("不等于") {
        @Override
        public boolean test(double value, double threshold) {
            return Math.abs(value - threshold) >= 1.0E-9;
        }
    };

    private final String description;

    AlertOperator(String description) {
        this.description = description;
    }

    public abstract boolean test(double value, double threshold);

    public String getDescription() {
        return description;
    }

    public static AlertOperator parse(String value, AlertOperator defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        switch (normalized) {
            case ">", "GT" -> {
                return GREATER_THAN;
            }
            case ">=", "GE", "GTE" -> {
                return GREATER_OR_EQUAL;
            }
            case "<", "LT" -> {
                return LESS_THAN;
            }
            case "<=", "LE", "LTE" -> {
                return LESS_OR_EQUAL;
            }
            case "==", "EQ" -> {
                return EQUAL;
            }
            case "!=", "NE", "NEQ" -> {
                return NOT_EQUAL;
            }
            default -> {
            }
        }
        for (AlertOperator operator : values()) {
            if (operator.name().equals(normalized)) {
                return operator;
            }
        }
        return defaultValue;
    }
}
