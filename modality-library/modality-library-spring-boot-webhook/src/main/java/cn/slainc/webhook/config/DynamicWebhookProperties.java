package cn.slainc.webhook.config;

import cn.slainc.core.config.DynamicConfigService;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

public class DynamicWebhookProperties extends WebhookProperties {

    public static final String KEY_ENABLED = "modality.webhook.enabled";

    public static final String KEY_MAX_RETRY = "modality.webhook.max-retry";

    public static final String KEY_TIMEOUT_MILLIS = "modality.webhook.timeout-millis";

    public static final String KEY_INITIAL_BACKOFF = "modality.webhook.initial-backoff";

    public static final String KEY_MAX_BACKOFF = "modality.webhook.max-backoff";

    public static final String KEY_BATCH_SIZE = "modality.webhook.batch-size";

    public static final String KEY_SIGNATURE_TOLERANCE_SECONDS = "modality.webhook.signature-tolerance-seconds";

    public static final String KEY_SIGNATURE_HEADER = "modality.webhook.signature-header";

    private final WebhookProperties fallback;

    private final ObjectProvider<DynamicConfigService> configService;

    public DynamicWebhookProperties(WebhookProperties fallback, ObjectProvider<DynamicConfigService> configService) {
        this.fallback = fallback == null ? new WebhookProperties() : fallback;
        this.configService = configService;
    }

    @Override
    public boolean isEnabled() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.isEnabled();
        }
        try {
            return service.getBoolean(KEY_ENABLED, fallback.isEnabled());
        } catch (RuntimeException e) {
            return fallback.isEnabled();
        }
    }

    @Override
    public int getMaxRetry() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getMaxRetry();
        }
        try {
            return service.getInt(KEY_MAX_RETRY, fallback.getMaxRetry());
        } catch (RuntimeException e) {
            return fallback.getMaxRetry();
        }
    }

    @Override
    public long getTimeoutMillis() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getTimeoutMillis();
        }
        try {
            return service.getLong(KEY_TIMEOUT_MILLIS, fallback.getTimeoutMillis());
        } catch (RuntimeException e) {
            return fallback.getTimeoutMillis();
        }
    }

    @Override
    public Duration getInitialBackoff() {
        return duration(KEY_INITIAL_BACKOFF, fallback.getInitialBackoff());
    }

    @Override
    public Duration getMaxBackoff() {
        return duration(KEY_MAX_BACKOFF, fallback.getMaxBackoff());
    }

    @Override
    public int getBatchSize() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getBatchSize();
        }
        try {
            return service.getInt(KEY_BATCH_SIZE, fallback.getBatchSize());
        } catch (RuntimeException e) {
            return fallback.getBatchSize();
        }
    }

    @Override
    public long getSignatureToleranceSeconds() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getSignatureToleranceSeconds();
        }
        try {
            return service.getLong(KEY_SIGNATURE_TOLERANCE_SECONDS, fallback.getSignatureToleranceSeconds());
        } catch (RuntimeException e) {
            return fallback.getSignatureToleranceSeconds();
        }
    }

    @Override
    public String getSignatureHeader() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getSignatureHeader();
        }
        try {
            String value = service.getString(KEY_SIGNATURE_HEADER, null);
            return value == null || value.isBlank() ? fallback.getSignatureHeader() : value;
        } catch (RuntimeException e) {
            return fallback.getSignatureHeader();
        }
    }

    private Duration duration(String key, Duration fallbackValue) {
        DynamicConfigService service = service();
        if (service == null) {
            return fallbackValue;
        }
        try {
            String value = service.getString(key, null);
            if (value == null || value.isBlank()) {
                return fallbackValue;
            }
            return parseDuration(value.trim(), fallbackValue);
        } catch (RuntimeException e) {
            // 配置不可读时回落静态值
            return fallbackValue;
        }
    }

    private static Duration parseDuration(String value, Duration fallbackValue) {
        try {
            return Duration.parse(value);
        } catch (RuntimeException ignored) {
            // 非 ISO-8601 写法按秒解析
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (RuntimeException e) {
            return fallbackValue;
        }
    }

    private DynamicConfigService service() {
        return configService == null ? null : configService.getIfAvailable();
    }
}
