package cn.slainc.logger.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record AuditRecord(String id, Instant timestamp, String description, String module,
                          AuditOperation operation, AuditCategory category, AuditLevel level, boolean success,
                          Long userId, String loginId, String username, Long tenantId,
                          String clientIp, String userAgent, String deviceId,
                          String requestPath, String httpMethod, String traceId, String spanId, String parentSpanId,
                          String target, String params, String result, String beforeSnapshot, String afterSnapshot,
                          String exceptionClass, String exceptionMessage, long costMillis) {

    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("auditId", id);
        values.put("timestamp", timestamp == null ? null : timestamp.toString());
        values.put("description", description);
        values.put("module", module);
        values.put("operation", operation == null ? null : operation.name());
        values.put("operationLabel", operation == null ? null : operation.label());
        values.put("category", category == null ? null : category.name());
        values.put("categoryLabel", category == null ? null : category.label());
        values.put("level", level == null ? null : level.name());
        values.put("levelLabel", level == null ? null : level.label());
        values.put("success", success);
        values.put("userId", userId);
        values.put("loginId", loginId);
        values.put("username", username);
        values.put("tenantId", tenantId);
        values.put("clientIp", clientIp);
        values.put("userAgent", userAgent);
        values.put("deviceId", deviceId);
        values.put("requestPath", requestPath);
        values.put("httpMethod", httpMethod);
        values.put("traceId", traceId);
        values.put("spanId", spanId);
        values.put("parentSpanId", parentSpanId);
        values.put("target", target);
        values.put("params", params);
        values.put("result", result);
        values.put("beforeSnapshot", beforeSnapshot);
        values.put("afterSnapshot", afterSnapshot);
        values.put("exceptionClass", exceptionClass);
        values.put("exceptionMessage", exceptionMessage);
        values.put("costMillis", costMillis);
        return values;
    }
}
