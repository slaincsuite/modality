package cn.slainc.monitor.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookAlertNotifierTest {

    private final RecordingTransport transport = new RecordingTransport();

    private final AlertEvent event = AlertEvent.of(rule(AlertLevel.ERROR), AlertType.TRIGGER, 0.97d, 1_700_000_000_000L);

    @Test
    @DisplayName("钉钉使用 markdown 载荷并带上关键词")
    void shouldBuildDingtalkPayload() {
        WebhookDefinition definition = definition(WebhookPlatform.DINGTALK);
        definition.setKeyword("监控告警");
        definition.setHeaders(Map.of("X-Trace", "trace-1"));
        new WebhookAlertNotifier(definition, transport).send(event);
        assertThat(transport.url).isEqualTo("https://example.com/dingtalk");
        assertThat(transport.headers).containsEntry("X-Trace", "trace-1");
        assertThat(transport.body).contains("\"msgtype\":\"markdown\"").contains("监控告警").contains("磁盘使用率过高")
                .contains("告警触发");
    }

    @Test
    @DisplayName("钉钉配置密钥后按官方算法加签")
    void shouldSignDingtalkUrl() {
        WebhookDefinition definition = definition(WebhookPlatform.DINGTALK);
        definition.setSecret("SEC-demo");
        new WebhookAlertNotifier(definition, transport).send(event);
        assertThat(transport.url).startsWith("https://example.com/dingtalk?timestamp=").contains("&sign=");
        // 期望值由 openssl 独立复核：printf '%s' "1700000000000\nSEC-demo" | openssl dgst -sha256 -hmac SEC-demo -binary | base64
        assertThat(WebhookAlertNotifier.sign(1_700_000_000_000L, "SEC-demo"))
                .isEqualTo("D/xDN+jQpSknk8+PAQJ9+QrB8UoyJMfIh+tA1ge3QFE=");
        String encodedSign = transport.url.substring(transport.url.indexOf("&sign=") + 6);
        assertThat(java.net.URLDecoder.decode(encodedSign, java.nio.charset.StandardCharsets.UTF_8))
                .matches("[A-Za-z0-9+/]{43}=");
    }

    @Test
    @DisplayName("飞书使用 interactive 卡片并区分级别颜色")
    void shouldBuildFeishuPayload() {
        new WebhookAlertNotifier(definition(WebhookPlatform.FEISHU), transport).send(event);
        assertThat(transport.body).contains("\"msg_type\":\"interactive\"").contains("\"template\":\"red\"")
                .contains("磁盘使用率过高");
        AlertEvent warn = AlertEvent.of(rule(AlertLevel.WARN), AlertType.RECOVER, 0.1d, 1L);
        new WebhookAlertNotifier(definition(WebhookPlatform.FEISHU), transport).send(warn);
        assertThat(transport.body).contains("\"template\":\"orange\"").contains("告警恢复");
    }

    @Test
    @DisplayName("企业微信使用 markdown 载荷")
    void shouldBuildWecomPayload() {
        new WebhookAlertNotifier(definition(WebhookPlatform.WECOM), transport).send(event);
        assertThat(transport.body).contains("\"msgtype\":\"markdown\"")
                .contains("\"markdown\":{\"content\":\"[严重] 磁盘使用率过高").contains("告警触发");
    }

    @Test
    @DisplayName("AlertManager 使用标准 alerts 数组")
    void shouldBuildAlertManagerPayload() {
        new WebhookAlertNotifier(definition(WebhookPlatform.ALERT_MANAGER), transport).send(event);
        assertThat(transport.body).contains("\"status\":\"firing\"").contains("\"alertname\":\"磁盘使用率过高\"")
                .contains("\"severity\":\"error\"").contains("\"endsAt\":null");
        AlertEvent recover = AlertEvent.of(rule(AlertLevel.WARN), AlertType.RECOVER, 0.1d, 1_700_000_000_000L);
        new WebhookAlertNotifier(definition(WebhookPlatform.ALERT_MANAGER), transport).send(recover);
        assertThat(transport.body).contains("\"status\":\"resolved\"").contains("2023-11-14T22:13:20Z");
    }

    @Test
    @DisplayName("自定义平台按模板渲染并转义 JSON")
    void shouldRenderCustomTemplate() {
        WebhookDefinition definition = definition(WebhookPlatform.CUSTOM);
        definition.setTemplate("{\"text\":\"{{ruleName}}\",\"level\":\"{{level}}\",\"value\":{{value}}}");
        new WebhookAlertNotifier(definition, transport).send(event);
        assertThat(transport.body).isEqualTo("{\"text\":\"磁盘使用率过高\",\"level\":\"ERROR\",\"value\":0.97}");
    }

    @Test
    @DisplayName("未配置模板时发送结构化事件字段")
    void shouldSendStructuredPayloadWithoutTemplate() {
        new WebhookAlertNotifier(definition(WebhookPlatform.CUSTOM), transport).send(event);
        assertThat(transport.body).contains("\"ruleId\":\"disk-usage-high\"").contains("\"type\":\"TRIGGER\"")
                .contains("\"threshold\":0.9");
    }

    @Test
    @DisplayName("Webhook 返回非 0 错误码时抛异常并带上响应内容")
    void shouldFailOnErrorCode() {
        WebhookDefinition definition = definition(WebhookPlatform.DINGTALK);
        transport.response = "{\"errcode\":310000,\"errmsg\":\"keywords not in content\"}";
        assertThatThrownBy(() -> new WebhookAlertNotifier(definition, transport).send(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Webhook 返回错误码 310000")
                .hasMessageContaining("keywords not in content");
    }

    @Test
    @DisplayName("通知级别过滤：低于阈值的级别不发")
    void shouldFilterByMinLevel() {
        WebhookDefinition definition = definition(WebhookPlatform.DINGTALK);
        definition.setMinLevel(AlertLevel.ERROR);
        WebhookAlertNotifier notifier = new WebhookAlertNotifier(definition, transport);
        assertThat(notifier.minLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(notifier.supports(AlertEvent.of(rule(AlertLevel.WARN), AlertType.TRIGGER, 1d, 1L))).isFalse();
        assertThat(notifier.supports(event)).isTrue();
        assertThat(notifier.name()).isEqualTo("钉钉通道");
    }

    @Test
    @DisplayName("缺少 url 的配置直接报中文错误")
    void shouldRejectDefinitionWithoutUrl() {
        WebhookDefinition definition = new WebhookDefinition();
        definition.setName("空通道");
        assertThatThrownBy(definition::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少 url");
        assertThatThrownBy(() -> new WebhookAlertNotifier(null, transport))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("WebhookDefinition 不能为空");
    }

    private static AlertRule rule(AlertLevel level) {
        AlertRule rule = AlertRule.of("disk-usage-high", "磁盘使用率过高", "system.disk.usage",
                AlertOperator.GREATER_THAN, 0.9d, 600L, level);
        rule.setUnit("%");
        return rule;
    }

    private static WebhookDefinition definition(WebhookPlatform platform) {
        WebhookDefinition definition = new WebhookDefinition();
        definition.setName("钉钉通道");
        definition.setPlatform(platform);
        definition.setUrl("https://example.com/dingtalk");
        return definition;
    }

    private static class RecordingTransport implements WebhookTransport {

        private String url;

        private String body;

        private Map<String, String> headers = new LinkedHashMap<>();

        private String response = "{\"errcode\":0}";

        @Override
        public String post(String url, String body, Map<String, String> headers) {
            this.url = url;
            this.body = body;
            this.headers = headers == null ? Map.of() : headers;
            return response;
        }
    }
}
