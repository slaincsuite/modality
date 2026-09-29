package cn.slainc.modules.audit.alert;


public record AuditAlertMessage(String ruleId, String ruleName, String level, double value, double threshold,
                                String unit, long timestamp, String message) {
}
