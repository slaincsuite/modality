package cn.slainc.modules.infra.service;

import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.crypto.hmac.HmacAlgorithm;
import cn.slainc.crypto.hmac.HmacUtils;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
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
                default -> "{\"title\":" + quote(title) + ",\"content\":" + quote(content) + ",\"level\":"
                        + quote(level == null ? "INFO" : level) + "}";
            };
            String url = "DINGTALK".equals(platform) && config.secret() != null && !config.secret().isBlank()
                    ? signedDingtalkUrl(config.url(), config.secret())
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
            if (response.body() != null && response.body().contains("\"errcode\"")
                    && !response.body().contains("\"errcode\":0")) {
                return SendOutcome.fail("平台返回错误：" + truncate(response.body()));
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
        String text = "### " + nullToEmpty(title) + "\n\n" + nullToEmpty(content);
        if (level != null && !"INFO".equals(level)) {
            text = "### [" + level + "] " + nullToEmpty(title) + "\n\n" + nullToEmpty(content);
        }
        return "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":" + quote(title) + ",\"text\":" + quote(text) + "}}";
    }

    private static String feishuBody(String title, String content, String level) {
        String prefix = level == null || "INFO".equals(level) ? "" : "[" + level + "] ";
        return "{\"msgtype\":\"text\",\"content\":{\"text\":" + quote(prefix + nullToEmpty(title) + "\n"
                + nullToEmpty(content)) + "}}";
    }

    private static String wecomBody(String title, String content, String level) {
        String prefix = level == null || "INFO".equals(level) ? "" : "[" + level + "] ";
        return "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":" + quote(prefix + nullToEmpty(title) + "\n"
                + nullToEmpty(content)) + "}}";
    }

    private static String signedDingtalkUrl(String url, String secret) {
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        String sign = Base64.getEncoder().encodeToString(
                HmacUtils.hmac(HmacAlgorithm.HMAC_SHA256, secret.getBytes(StandardCharsets.UTF_8), stringToSign));
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "timestamp=" + timestamp + "&sign="
                + URLEncoder.encode(sign, StandardCharsets.UTF_8);
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

    private static String quote(String value) {
        return "\"" + nullToEmpty(value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "") + "\"";
    }
}
