package cn.slainc.monitor.alert;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.config.MonitorDynamicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class WebhookNotifierSource implements AlertNotifierSource {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifierSource.class);

    public static final String WEBHOOKS_KEY = "modality.monitor.alert.webhooks";

    private final MonitorDynamicConfig config;

    private final MonitorProperties properties;

    private final WebhookTransport transport;

    private final long cacheMillis;

    private volatile List<AlertNotifier> cached = List.of();

    private volatile String cachedSignature = "";

    private volatile long cachedAt;

    public WebhookNotifierSource(MonitorDynamicConfig config, MonitorProperties properties, WebhookTransport transport,
                                 long cacheMillis) {
        this.config = config;
        this.properties = properties;
        this.transport = transport;
        this.cacheMillis = Math.max(cacheMillis, 0L);
    }

    @Override
    public List<AlertNotifier> notifiers() {
        if (System.currentTimeMillis() - cachedAt < cacheMillis) {
            return cached;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - cachedAt < cacheMillis) {
                return cached;
            }
            List<WebhookDefinition> definitions = definitions();
            String signature = signature(definitions);
            if (!signature.equals(cachedSignature)) {
                cached = build(definitions);
                cachedSignature = signature;
            }
            cachedAt = System.currentTimeMillis();
            return cached;
        }
    }

    public List<WebhookDefinition> definitions() {
        List<WebhookDefinition> dynamic = config.getList(WEBHOOKS_KEY, WebhookDefinition.class);
        if (!dynamic.isEmpty()) {
            return dynamic;
        }
        List<WebhookDefinition> configured = properties.getAlert().getWebhooks();
        return configured == null ? List.of() : configured;
    }

    private List<AlertNotifier> build(List<WebhookDefinition> definitions) {
        List<AlertNotifier> notifiers = new ArrayList<>();
        for (WebhookDefinition definition : definitions) {
            if (definition == null || !definition.isEnabled()) {
                continue;
            }
            try {
                definition.validate(properties.getAlert().isAllowInternalWebhooks());
                notifiers.add(new WebhookAlertNotifier(definition, transport));
            } catch (RuntimeException exception) {
                log.warn("忽略非法的告警 Webhook 配置: {}", exception.getMessage());
            }
        }
        return List.copyOf(notifiers);
    }

    private static String signature(List<WebhookDefinition> definitions) {
        StringBuilder builder = new StringBuilder();
        for (WebhookDefinition definition : definitions) {
            builder.append(definition.signature()).append(';');
        }
        return builder.toString();
    }
}
