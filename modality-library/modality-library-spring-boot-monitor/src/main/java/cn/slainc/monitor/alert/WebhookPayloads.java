package cn.slainc.monitor.alert;

import cn.slainc.utils.json.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WebhookPayloads {

    private static final String NL = String.valueOf((char) 10);

    private WebhookPayloads() {
    }

    public static String of(WebhookDefinition definition, AlertEvent event) {
        return switch (definition.getPlatform()) {
            case DINGTALK -> dingtalk(definition, event);
            case FEISHU -> feishu(definition, event);
            case WECOM -> wecom(definition, event);
            case ALERT_MANAGER -> alertManager(event);
            case CUSTOM -> custom(definition, event);
        };
    }

    public static String title(WebhookDefinition definition, AlertEvent event) {
        String prefix = definition.getKeyword() == null || definition.getKeyword().isBlank()
                ? "" : definition.getKeyword() + " ";
        return prefix + "[" + event.level().getDescription() + "] " + event.ruleName();
    }

    public static String text(AlertEvent event) {
        List<String> lines = new ArrayList<>();
        lines.add("### 告警" + event.type().getDescription() + " | " + event.ruleName());
        lines.add("- 规则: " + event.ruleId());
        lines.add("- 指标: " + event.metric());
        lines.add("- 当前值: " + event.value() + event.unitSuffix());
        lines.add("- 阈值: " + event.threshold() + event.unitSuffix());
        lines.add("- 级别: " + event.level().getDescription());
        lines.add("- 时间: " + event.instant());
        lines.add("- 说明: " + event.message());
        return String.join(NL, lines);
    }

    public static String dingtalk(WebhookDefinition definition, AlertEvent event) {
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", title(definition, event));
        markdown.put("text", text(event));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "markdown");
        payload.put("markdown", markdown);
        return JsonUtils.toJson(payload);
    }

    public static String feishu(WebhookDefinition definition, AlertEvent event) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", Map.of("tag", "plain_text", "content", title(definition, event)));
        header.put("template", cardColor(event.level()));
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("tag", "div");
        element.put("text", Map.of("tag", "lark_md", "content", text(event)));
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", header);
        card.put("elements", List.of(element));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msg_type", "interactive");
        payload.put("card", card);
        return JsonUtils.toJson(payload);
    }

    public static String wecom(WebhookDefinition definition, AlertEvent event) {
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", title(definition, event) + NL + text(event));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "markdown");
        payload.put("markdown", markdown);
        return JsonUtils.toJson(payload);
    }

    public static String alertManager(AlertEvent event) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("alertname", event.ruleName());
        labels.put("severity", event.level().name().toLowerCase(java.util.Locale.ROOT));
        labels.put("rule", event.ruleId());
        labels.put("metric", event.metric());
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("summary", event.ruleName());
        annotations.put("description", event.message());
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("status", event.type() == AlertType.TRIGGER ? "firing" : "resolved");
        alert.put("labels", labels);
        alert.put("annotations", annotations);
        alert.put("startsAt", event.instant().toString());
        alert.put("endsAt", event.type() == AlertType.RECOVER ? event.instant().toString() : null);
        return JsonUtils.toJson(List.of(alert));
    }

    public static String custom(WebhookDefinition definition, AlertEvent event) {
        String template = definition.getTemplate();
        if (template == null || template.isBlank()) {
            return JsonUtils.toJson(eventMap(event));
        }
        return template
                .replace("{{ruleId}}", json(event.ruleId()))
                .replace("{{ruleName}}", json(event.ruleName()))
                .replace("{{level}}", json(event.level().name()))
                .replace("{{type}}", json(event.type().name()))
                .replace("{{metric}}", json(event.metric()))
                .replace("{{value}}", String.valueOf(event.value()))
                .replace("{{threshold}}", String.valueOf(event.threshold()))
                .replace("{{timestamp}}", String.valueOf(event.timestamp()))
                .replace("{{time}}", json(event.instant().toString()))
                .replace("{{message}}", json(event.message()));
    }

    private static Map<String, Object> eventMap(AlertEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleId", event.ruleId());
        map.put("ruleName", event.ruleName());
        map.put("level", event.level().name());
        map.put("type", event.type().name());
        map.put("metric", event.metric());
        map.put("value", event.value());
        map.put("threshold", event.threshold());
        map.put("timestamp", event.timestamp());
        map.put("time", event.instant().toString());
        map.put("message", event.message());
        return map;
    }

    private static String cardColor(AlertLevel level) {
        return switch (level) {
            case ERROR -> "red";
            case WARN -> "orange";
            default -> "blue";
        };
    }

    private static String json(String value) {
        String encoded = JsonUtils.toJson(value == null ? "" : value);
        if (encoded != null && encoded.length() >= 2) {
            return encoded.substring(1, encoded.length() - 1);
        }
        return "";
    }
}
