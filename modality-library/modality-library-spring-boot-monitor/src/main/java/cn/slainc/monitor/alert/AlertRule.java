package cn.slainc.monitor.alert;

import java.util.LinkedHashMap;
import java.util.Map;

public class AlertRule {

    private String id;

    private String name;

    private String metric;

    private AlertOperator operator = AlertOperator.GREATER_THAN;

    private double threshold;

    private long durationSeconds = 60L;

    private AlertLevel level = AlertLevel.WARN;

    private long suppressSeconds = -1L;

    private String unit = "";

    private String description;

    private boolean enabled = true;

    private Map<String, String> tags = new LinkedHashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public AlertOperator getOperator() {
        return operator;
    }

    public void setOperator(AlertOperator operator) {
        this.operator = operator == null ? AlertOperator.GREATER_THAN : operator;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public AlertLevel getLevel() {
        return level;
    }

    public void setLevel(AlertLevel level) {
        this.level = level == null ? AlertLevel.WARN : level;
    }

    public String getCompare() {
        return operator.name();
    }

    public void setCompare(String compare) {
        this.operator = AlertOperator.parse(compare, this.operator);
    }

    public long getSuppressSeconds() {
        return suppressSeconds;
    }

    public void setSuppressSeconds(long suppressSeconds) {
        this.suppressSeconds = suppressSeconds;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags == null ? new LinkedHashMap<>() : tags;
    }

    public String displayName() {
        return name == null || name.isBlank() ? id : name;
    }

    public long resolvedSuppressSeconds(long defaultSeconds) {
        return suppressSeconds < 0L ? defaultSeconds : suppressSeconds;
    }

    public boolean breached(double value) {
        return operator.test(value, threshold);
    }

    public void validate() {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("告警规则缺少 id");
        }
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("告警规则 " + id + " 缺少 metric");
        }
        if (durationSeconds < 0L) {
            throw new IllegalArgumentException("告警规则 " + id + " 的 durationSeconds 不能为负数");
        }
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("告警规则 " + id + " 的 threshold 不是合法数字");
        }
    }

    public static AlertRule of(String id, String name, String metric, AlertOperator operator, double threshold,
                               long durationSeconds, AlertLevel level) {
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setName(name);
        rule.setMetric(metric);
        rule.setOperator(operator);
        rule.setThreshold(threshold);
        rule.setDurationSeconds(durationSeconds);
        rule.setLevel(level);
        return rule;
    }
}
