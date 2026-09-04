package cn.slainc.monitor.alert;

import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class WebhookAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookAlertNotifier.class);

    private final WebhookDefinition definition;

    private final WebhookTransport transport;

    public WebhookAlertNotifier(WebhookDefinition definition, WebhookTransport transport) {
        if (definition == null) {
            throw new IllegalArgumentException("WebhookDefinition 不能为空");
        }
        if (transport == null) {
            throw new IllegalArgumentException("WebhookTransport 不能为空");
        }
        this.definition = definition;
        this.transport = transport;
    }

    @Override
    public String name() {
        return definition.resolvedName();
    }

    @Override
    public AlertLevel minLevel() {
        return definition.getMinLevel();
    }

    public WebhookDefinition definition() {
        return definition;
    }

    @Override
    public void send(AlertEvent event) {
        definition.validate();
        String url = signedUrl(definition);
        String body = WebhookPayloads.of(definition, event);
        verifyResponse(transport.post(url, body, definition.getHeaders()));
    }

    public static String signedUrl(WebhookDefinition definition) {
        String url = definition.getUrl();
        if (definition.getPlatform() != WebhookPlatform.DINGTALK) {
            return url;
        }
        String secret = definition.getSecret();
        if (secret == null || secret.isBlank()) {
            return url;
        }
        long timestamp = System.currentTimeMillis();
        String sign = sign(timestamp, secret);
        return url + (url.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign="
                + URLEncoder.encode(sign, StandardCharsets.UTF_8);
    }

    public static String sign(long timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String content = timestamp + String.valueOf((char) 10) + secret;
            return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("钉钉 Webhook 加签失败: " + exception.getMessage(), exception);
        }
    }

    private void verifyResponse(String response) {
        if (response == null || response.isBlank()) {
            return;
        }
        try {
            Map<String, Object> body = JsonUtils.parseMap(response);
            Object code = body.get("errcode") == null ? body.get("code") : body.get("errcode");
            if (code instanceof Number number && number.intValue() != 0) {
                throw new IllegalStateException("Webhook 返回错误码 " + number + "，响应内容: " + response);
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.debug("Webhook 响应不是合法 JSON，跳过错误码校验: {}", exception.getMessage());
        }
    }
}
