package cn.slainc.monitor.alert;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.config.MonitorDynamicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AlertSilenceRegistry {

    private static final Logger log = LoggerFactory.getLogger(AlertSilenceRegistry.class);

    public static final String SILENCE_UNTIL_KEY = "modality.monitor.alert.silence.until";

    public static final String SILENCE_RULES_KEY = "modality.monitor.alert.silence.rules";

    public static final String ALL = "*";

    private final MonitorDynamicConfig config;

    private final MonitorProperties properties;

    private final Map<String, Long> silences = new ConcurrentHashMap<>();

    public AlertSilenceRegistry(MonitorDynamicConfig config, MonitorProperties properties) {
        this.config = config;
        this.properties = properties;
    }

    public void silence(String ruleId, Duration duration) {
        if (ruleId == null || ruleId.isBlank() || duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        silences.put(ruleId, System.currentTimeMillis() + duration.toMillis());
    }

    public void silenceAll(Duration duration) {
        silence(ALL, duration);
    }

    public void cancel(String ruleId) {
        if (ruleId != null) {
            silences.remove(ruleId);
        }
    }

    public void cancelAll() {
        silences.clear();
    }

    public boolean isSilenced(AlertRule rule, long nowMillis) {
        String ruleId = rule == null || rule.getId() == null ? ALL : rule.getId();
        return isSilenced(ruleId, nowMillis);
    }

    public boolean isSilenced(String ruleId, long nowMillis) {
        return isSilencedProgrammatically(ruleId, nowMillis) || isSilencedByConfig(ruleId, nowMillis);
    }

    public boolean anySilenced() {
        long now = System.currentTimeMillis();
        return silences.values().stream().anyMatch(until -> until > now) || configUntil() > now;
    }

    private boolean isSilencedProgrammatically(String ruleId, long nowMillis) {
        Long until = silences.get(ruleId);
        if (until != null) {
            if (until > nowMillis) {
                return true;
            }
            silences.remove(ruleId);
        }
        Long all = silences.get(ALL);
        if (all != null) {
            if (all > nowMillis) {
                return true;
            }
            silences.remove(ALL);
        }
        return false;
    }

    private boolean isSilencedByConfig(String ruleId, long nowMillis) {
        if (configUntil() <= nowMillis) {
            return false;
        }
        Set<String> rules = configRules();
        return rules.isEmpty() || rules.contains(ALL) || rules.contains(ruleId);
    }

    private long configUntil() {
        String value = config.getString(SILENCE_UNTIL_KEY, properties.getAlert().getSilence().getUntil());
        if (value == null || value.isBlank()) {
            return 0L;
        }
        String trimmed = value.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException exception) {
            try {
                return Instant.parse(trimmed).toEpochMilli();
            } catch (DateTimeParseException parseException) {
                log.warn("静默截止时间 {} 无法解析，支持毫秒时间戳或 ISO-8601，已忽略本次静默", trimmed);
                return 0L;
            }
        }
    }

    private Set<String> configRules() {
        String value = config.getString(SILENCE_RULES_KEY,
                String.join(",", properties.getAlert().getSilence().getRules()));
        Set<String> rules = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return rules;
        }
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                rules.add(trimmed);
            }
        }
        return rules;
    }
}
