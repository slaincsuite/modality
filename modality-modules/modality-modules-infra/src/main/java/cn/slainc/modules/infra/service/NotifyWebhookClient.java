package cn.slainc.modules.infra.service;

import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.monitor.alert.WebhookSignatures;
import cn.slainc.utils.json.JsonUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class NotifyWebhookClient {

    public record WebhookConfig(long id, String name, String platform, String url, String secret) {
    }

    public record SendOutcome(String status, String error) {

        public static SendOutcome ok() {
            return new SendOutcome("SUCCESS", null);
        }

        public static SendOutcome fail(String error) {
            return new SendOutcome("FAILED", error);
        }
    }

    private static final Set<String> PLATFORMS = Set.of("DINGTALK", "FEISHU", "WECOM", "CUSTOM");

    private final HttpClient client;

    public NotifyWebhookClient(int connectTimeoutMillis, int readTimeoutMillis) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
    }

    public SendOutcome send(WebhookConfig config, String title, String content, String level) {
        try {
            BizAssert.hasText(config.url(), "Webhook 地址不能为空");
            String platform = config.platform() == null ? "CUSTOM" : config.platform().toUpperCase();
            BizAssert.isTrue(PLATFORMS.contains(platform), "Webhook 平台只允许 DINGTALK、FEISHU、WECOM、CUSTOM");
            String body = switch (platform) {
                case "DINGTALK" -> dingtalkBody(title, content, level);
                case "FEISHU" -> feishuBody(title, content, level);
                case "WECOM" -> wecomBody(title, content, level);
                default -> customBody(title, content, level);
            };
            String url = "DINGTALK".equals(platform) && config.secret() != null && !config.secret().isBlank()
                    ? WebhookSignatures.dingTalkSignedUrl(config.url(), config.secret(), System.currentTimeMillis())
                    : config.url();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return SendOutcome.fail("HTTP " + response.statusCode() + "：" + truncate(response.body()));
            }
            try {
                WebhookSignatures.verifyPlatformResponse(response.body());
            } catch (IllegalStateException exception) {
                return SendOutcome.fail(truncate(exception.getMessage()));
            }
            return SendOutcome.ok();
        } catch (ThirdPartyException exception) {
            return SendOutcome.fail(exception.getMessage());
        } catch (Exception exception) {
            return SendOutcome.fail(exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage());
        }
    }

    private static String dingtalkBody(String title, String content, String level) {
        String heading = level != null && !"INFO".equals(level)
                ? "### [" + level + "] " + nullToEmpty(title)
                : "### " + nullToEmpty(title);
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", nullToEmpty(title));
        markdown.put("text", heading + "\n\n" + nullToEmpty(content));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);
        return JsonUtils.toJson(body);
    }

    private static String feishuBody(String title, String content, String level) {
        String prefix = level == null || "INFO".equals(level) ? "" : "[" + level + "] ";
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("text", prefix + nullToEmpty(title) + "\n" + nullToEmpty(content));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        body.put("content", text);
        return JsonUtils.toJson(body);
    }

    private static String wecomBody(String title, String content, String level) {
        String prefix = level == null || "INFO".equals(level) ? "" : "[" + level + "] ";
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", prefix + nullToEmpty(title) + "\n" + nullToEmpty(content));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);
        return JsonUtils.toJson(body);
    }

    private static String customBody(String title, String content, String level) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", nullToEmpty(title));
        body.put("content", nullToEmpty(content));
        body.put("level", level == null ? "INFO" : level);
        return JsonUtils.toJson(body);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
}
