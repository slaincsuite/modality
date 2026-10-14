package cn.slainc.mfa.config;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MfaConfigReader {

    public static final String KEY_ENABLED = "modality.mfa.enabled";

    public static final String KEY_ISSUER = "modality.mfa.issuer";

    public static final String KEY_DIGITS = "modality.mfa.digits";

    public static final String KEY_PERIOD_SECONDS = "modality.mfa.period-seconds";

    public static final String KEY_WINDOW = "modality.mfa.window";

    public static final String KEY_CHALLENGE_TTL = "modality.mfa.challenge-ttl";

    public static final String KEY_RECOVERY_CODE_COUNT = "modality.mfa.recovery-code-count";

    public static final String KEY_MAX_FAIL_COUNT = "modality.mfa.max-fail-count";

    public static final String KEY_LOCK_DURATION = "modality.mfa.lock-duration";

    public static final String KEY_REQUIRE_FOR_ROLES = "modality.mfa.require-for-roles";

    private final MfaProperties properties;

    private final ObjectProvider<DynamicConfigService> configService;

    public MfaConfigReader(MfaProperties properties, ObjectProvider<DynamicConfigService> configService) {
        this.properties = properties == null ? new MfaProperties() : properties;
        this.configService = configService;
    }

    public MfaProperties properties() {
        return properties;
    }

    public boolean enabled() {
        return getBoolean(KEY_ENABLED, properties.isEnabled());
    }

    public String issuer() {
        return getString(KEY_ISSUER, properties.getIssuer());
    }

    public int digits() {
        return getInt(KEY_DIGITS, properties.getDigits());
    }

    public int periodSeconds() {
        return getInt(KEY_PERIOD_SECONDS, properties.getPeriodSeconds());
    }

    public int window() {
        return getInt(KEY_WINDOW, properties.getWindow());
    }

    public Duration challengeTtl() {
        return getDuration(KEY_CHALLENGE_TTL, properties.getChallengeTtl());
    }

    public int recoveryCodeCount() {
        return getInt(KEY_RECOVERY_CODE_COUNT, properties.getRecoveryCodeCount());
    }

    public int maxFailCount() {
        return getInt(KEY_MAX_FAIL_COUNT, properties.getMaxFailCount());
    }

    public Duration lockDuration() {
        return getDuration(KEY_LOCK_DURATION, properties.getLockDuration());
    }

    public List<String> requireForRoles() {
        return getList(KEY_REQUIRE_FOR_ROLES, properties.getRequireForRoles());
    }

    public Optional<String> findString(String key) {
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service == null) {
            return Optional.empty();
        }
        try {
            return service.findString(key);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String getString(String key, String defaultValue) {
        return findString(key).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service == null) {
            return defaultValue;
        }
        try {
            return service.getInt(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service == null) {
            return defaultValue;
        }
        try {
            return service.getBoolean(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public Duration getDuration(String key, Duration defaultValue) {
        String raw = findString(key).orElse(null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String text = raw.trim();
        try {
            return Duration.parse(text);
        } catch (Exception ignored) {
            // 兼容纯数字秒数的写法，例如 300 表示 5 分钟
            if (text.chars().allMatch(Character::isDigit)) {
                return Duration.ofSeconds(Long.parseLong(text));
            }
        }
        return defaultValue;
    }

    public List<String> getList(String key, List<String> defaultValue) {
        String raw = findString(key).orElse(null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String text = raw.trim();
        if (text.startsWith("[")) {
            try {
                List<String> parsed = JsonUtils.parseList(text, String.class);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // JSON 解析失败时按逗号分隔回落
            }
        }
        return text.isEmpty() ? defaultValue
                : Arrays.stream(text.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
