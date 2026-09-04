package cn.slainc.monitor.alert;

import java.time.Instant;

public record AlertEvent(String ruleId, String ruleName, AlertLevel level, AlertType type, String metric, String unit,
                         double value, double threshold, long timestamp, String message) {

    public static AlertEvent of(AlertRule rule, AlertType type, double value, long timestamp) {
        return new AlertEvent(rule.getId(), rule.displayName(), rule.getLevel(), type, rule.getMetric(), rule.getUnit(),
                value, rule.getThreshold(), timestamp, buildMessage(rule, type, value));
    }

    public Instant instant() {
        return Instant.ofEpochMilli(timestamp);
    }

    public String unitSuffix() {
        return unit == null || unit.isBlank() ? "" : unit;
    }

    private static String buildMessage(AlertRule rule, AlertType type, double value) {
        String suffix = rule.getUnit() == null || rule.getUnit().isBlank() ? "" : rule.getUnit();
        StringBuilder builder = new StringBuilder("告警");
        builder.append(type.getDescription()).append(" | 规则=").append(rule.displayName())
                .append('(').append(rule.getId()).append(')')
                .append(" 指标=").append(rule.getMetric())
                .append(" 当前值=").append(format(value)).append(suffix)
                .append(" 阈值=").append(format(rule.getThreshold())).append(suffix)
                .append(" 比较=").append(rule.getOperator().getDescription())
                .append(" 级别=").append(rule.getLevel().getDescription());
        if (type == AlertType.TRIGGER) {
            builder.append(" 持续=").append(rule.getDurationSeconds()).append("秒");
        }
        return builder.toString();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
