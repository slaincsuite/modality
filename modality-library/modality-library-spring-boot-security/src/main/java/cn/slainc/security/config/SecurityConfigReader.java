package cn.slainc.security.config;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.enums.LoginType;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SecurityConfigReader {

    private final SecurityProperties properties;

    private final ObjectProvider<DynamicConfigService> configService;

    public SecurityConfigReader(SecurityProperties properties, ObjectProvider<DynamicConfigService> configService) {
        this.properties = properties;
        this.configService = configService;
    }

    public SecurityProperties properties() {
        return properties;
    }

    public Optional<String> findString(String key) {
        DynamicConfigService service = configService.getIfAvailable();
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
        DynamicConfigService service = configService.getIfAvailable();
        if (service == null) {
            return defaultValue;
        }
        try {
            return service.getInt(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        DynamicConfigService service = configService.getIfAvailable();
        if (service == null) {
            return defaultValue;
        }
        try {
            return service.getLong(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        DynamicConfigService service = configService.getIfAvailable();
        if (service == null) {
            return defaultValue;
        }
        try {
            return service.getBoolean(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public List<String> getList(String key, List<String> defaultValue) {
        String raw = findString(key).orElse(null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            List<String> parsed = JsonUtils.parseList(raw, String.class);
            return parsed == null ? defaultValue : parsed;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public int permissionCacheTtlSeconds() {
        return getInt("modality.security.permission-cache-ttl-seconds", properties.getPermissionCacheTtlSeconds());
    }

    public long sessionTimeout(LoginType type) {
        return getLong("modality.security.session.timeout-" + type.value().toLowerCase(Locale.ROOT),
                sessionTimeoutOf(type));
    }

    private long sessionTimeoutOf(LoginType type) {
        return switch (type) {
            case ADMIN -> properties.getSession().getTimeoutAdmin();
            case USER -> properties.getSession().getTimeoutUser();
            case CLIENT -> properties.getSession().getTimeoutClient();
        };
    }

    public int lockoutMaxFailures() {
        return getInt("modality.security.lockout.max-failures", properties.getLockout().getMaxFailures());
    }

    public int lockoutWindowSeconds() {
        return getInt("modality.security.lockout.window-seconds", properties.getLockout().getWindowSeconds());
    }

    public int lockoutLockMinutes() {
        return getInt("modality.security.lockout.lock-minutes", properties.getLockout().getLockMinutes());
    }

    public int deviceMaxCountPerType() {
        return getInt("modality.security.device.max-count-per-type", properties.getDevice().getMaxCountPerType());
    }

    public DeviceOverflowMode deviceOverflowMode() {
        String raw = getString("modality.security.device.overflow-mode",
                properties.getDevice().getOverflowMode().name());
        try {
            return DeviceOverflowMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return DeviceOverflowMode.KICK_OLDEST;
        }
    }

    public boolean deviceSingleSessionPerType() {
        return getBoolean("modality.security.device.single-session-per-type",
                properties.getDevice().isSingleSessionPerType());
    }

    public String typeHeader() {
        return getString("modality.security.type-header", properties.getTypeHeader());
    }

    public String defaultTypeName() {
        return getString("modality.security.default-type", properties.getDefaultType());
    }

    public String deviceIdHeader() {
        return getString("modality.security.device-id-header", properties.getDeviceIdHeader());
    }

    public List<String> filterExcludePatterns() {
        return getList("modality.security.filter.exclude-patterns", properties.getFilter().getExcludePatterns());
    }

    public List<String> filterBlacklistPatterns() {
        return getList("modality.security.filter.blacklist-patterns", properties.getFilter().getBlacklistPatterns());
    }

    public List<String> filterProtectedPatterns() {
        return getList("modality.security.filter.protected-patterns", properties.getFilter().getProtectedPatterns());
    }

    public boolean originEnabled() {
        return getBoolean("modality.security.origin.enabled", properties.getOrigin().isEnabled());
    }

    public List<String> originAllowedOrigins() {
        return getList("modality.security.origin.allowed-origins", properties.getOrigin().getAllowedOrigins());
    }

    public List<String> restrictionIpWhitelist() {
        return getList("modality.security.restriction.ip-whitelist", properties.getRestriction().getIpWhitelist());
    }

    public List<String> restrictionIpBlacklist() {
        return getList("modality.security.restriction.ip-blacklist", properties.getRestriction().getIpBlacklist());
    }

    public String restrictionTimeStart() {
        return getString("modality.security.restriction.time-start", properties.getRestriction().getTimeStart());
    }

    public String restrictionTimeEnd() {
        return getString("modality.security.restriction.time-end", properties.getRestriction().getTimeEnd());
    }

    public List<String> restrictionAllowedDeviceTypes() {
        return getList("modality.security.restriction.allowed-device-types",
                properties.getRestriction().getAllowedDeviceTypes());
    }

    public int passwordMinLength() {
        return getInt("modality.security.password.min-length", properties.getPassword().getMinLength());
    }

    public int passwordRequireClasses() {
        return getInt("modality.security.password.require-classes", properties.getPassword().getRequireClasses());
    }

    public int passwordHistorySize() {
        return getInt("modality.security.password.history-size", properties.getPassword().getHistorySize());
    }

    public int passwordMaxAgeDays() {
        return getInt("modality.security.password.max-age-days", properties.getPassword().getMaxAgeDays());
    }
}
