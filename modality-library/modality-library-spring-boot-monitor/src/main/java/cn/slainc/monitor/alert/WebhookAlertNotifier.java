package cn.slainc.monitor.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        // 内网地址限制由 transport 按 allow-internal-webhooks 统一裁决，这里只校验 scheme/主机
        definition.validate(true);
        String url = signedUrl(definition);
        String body = WebhookPayloads.of(definition, event);
        WebhookSignatures.verifyPlatformResponse(transport.post(url, body, definition.getHeaders()));
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
        return WebhookSignatures.dingTalkSignedUrl(url, secret, System.currentTimeMillis());
    }

    public static String sign(long timestamp, String secret) {
        return WebhookSignatures.dingTalkSign(timestamp, secret);
    }
}
