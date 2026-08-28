package cn.slainc.idempotent.support;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.autoconfigure.IdempotentProperties;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class IdempotentSettingsResolver {

    private static final Logger log = LoggerFactory.getLogger(IdempotentSettingsResolver.class);

    private final IdempotentProperties properties;

    private final ObjectProvider<DynamicConfigService> dynamicConfigService;

    public IdempotentSettingsResolver(IdempotentProperties properties,
                                      ObjectProvider<DynamicConfigService> dynamicConfigService) {
        if (properties == null) {
            throw new IllegalArgumentException("IdempotentProperties 不能为空");
        }
        this.properties = properties;
        this.dynamicConfigService = dynamicConfigService;
    }

    public IdempotentSettings resolve(IdempotentContext context) {
        Idempotent annotation = context.annotation();
        String methodId = context.methodId();
        boolean enabled = bool("enabled", methodId, properties.isEnabled());
        Duration ttl = annotation.ttl() > 0L
                ? Duration.ofSeconds(annotation.ttl())
                : duration("default-ttl", methodId, properties.getDefaultTtl());
        int maxRetries = annotation.maxRetries() >= 0
                ? annotation.maxRetries()
                : intValue("retry.max-retries", methodId, properties.getRetry().getMaxRetries());
        Duration retryWindow = annotation.retryWindow() > 0L
                ? Duration.ofSeconds(annotation.retryWindow())
                : duration("retry.window", methodId, properties.getRetry().getWindow());
        String message = StringUtils.isNotBlank(annotation.message())
                ? annotation.message()
                : string("message", methodId, properties.getMessage());
        IdempotentStrategy strategy = annotation.strategy() == null
                ? IdempotentStrategy.PARAMETER : annotation.strategy();
        return new IdempotentSettings(enabled, strategy, annotation.handler(), message, positive(ttl),
                Math.max(maxRetries, 0), positive(retryWindow), ignoreFields(methodId, annotation),
                properties.isIncludeUserId(), properties.isIncludePath(), properties.isCacheResult(),
                properties.isFailOpen(), properties.isLogDuplicate());
    }

    private List<String> ignoreFields(String methodId, Idempotent annotation) {
        Set<String> merged = new LinkedHashSet<>();
        addAll(merged, properties.getIgnoreFields());
        addAll(merged, split(string("ignore-fields", methodId, null)));
        if (annotation.ignoreFields() != null) {
            addAll(merged, List.of(annotation.ignoreFields()));
        }
        return List.copyOf(merged);
    }

    private static void addAll(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                target.add(value.trim());
            }
        }
    }

    private static List<String> split(String value) {
        if (StringUtils.isBlank(value)) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split(",")) {
            if (StringUtils.isNotBlank(part)) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    private boolean bool(String key, String methodId, boolean defaultValue) {
        String value = dynamic(key, methodId, null);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private int intValue(String key, String methodId, int defaultValue) {
        String value = dynamic(key, methodId, null);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("动态配置 {} 不是合法整数，已退回默认值 {}，实际值={}", key, defaultValue, value);
            return defaultValue;
        }
    }

    private Duration duration(String key, String methodId, Duration defaultValue) {
        String value = dynamic(key, methodId, null);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        Duration parsed = parseDuration(value);
        return parsed == null ? defaultValue : parsed;
    }

    private String string(String key, String methodId, String defaultValue) {
        String value = dynamic(key, methodId, null);
        return StringUtils.isBlank(value) ? defaultValue : value.trim();
    }

    private String dynamic(String key, String methodId, String defaultValue) {
        DynamicConfigService service = dynamicConfigService == null ? null : dynamicConfigService.getIfAvailable();
        if (service == null) {
            return defaultValue;
        }
        if (StringUtils.isNotBlank(methodId)) {
            String value = read(service, methodKey(key, methodId));
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        String value = read(service, globalKey(key));
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    private String read(DynamicConfigService service, String key) {
        try {
            return service.findString(key).orElse(null);
        } catch (Exception exception) {
            log.warn("读取动态配置 {} 失败，已退回本地默认值，原因={}", key, exception.getMessage());
            return null;
        }
    }

    private static String globalKey(String key) {
        return IdempotentProperties.PREFIX + "." + key;
    }

    private static String methodKey(String key, String methodId) {
        return IdempotentProperties.PREFIX + ".methods." + methodId + "." + key;
    }

    private static Duration positive(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? Duration.ofMinutes(5) : value;
    }

    public static Duration parseDuration(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String value = text.trim();
        try {
            return Duration.parse(value.toUpperCase(Locale.ROOT));
        } catch (DateTimeParseException ignored) {
            // 非 ISO-8601 时按 30s / 5m / 2h / 1d / 500ms / 纯秒数 解析
        }
        String lower = value.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(lower.substring(0, lower.length() - 2).trim()));
            }
            if (lower.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(lower.substring(0, lower.length() - 1).trim()));
            }
            if (lower.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(lower.substring(0, lower.length() - 1).trim()));
            }
            if (lower.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(lower.substring(0, lower.length() - 1).trim()));
            }
            if (lower.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(lower.substring(0, lower.length() - 1).trim()));
            }
            return Duration.ofSeconds(Long.parseLong(lower));
        } catch (NumberFormatException exception) {
            log.warn("时间窗口 {} 无法解析，支持 30s / 5m / 2h / 1d / PT30S / 纯秒数", text);
            return null;
        }
    }
}
