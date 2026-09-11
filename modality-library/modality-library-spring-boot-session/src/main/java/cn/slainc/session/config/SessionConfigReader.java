package cn.slainc.session.config;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityConfigReader;
import org.springframework.beans.factory.ObjectProvider;

public class SessionConfigReader {

    private final SessionProperties properties;

    private final ObjectProvider<DynamicConfigService> configService;

    private final SecurityConfigReader securityConfig;

    public SessionConfigReader(SessionProperties properties, ObjectProvider<DynamicConfigService> configService,
                              SecurityConfigReader securityConfig) {
        this.properties = properties;
        this.configService = configService;
        this.securityConfig = securityConfig;
    }

    public SessionProperties properties() {
        return properties;
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

    public long ttlSeconds() {
        return getLong("modality.session.ttl-seconds", properties.getTtlSeconds());
    }

    public boolean maskToken() {
        return getBoolean("modality.session.display.mask-token", properties.getDisplay().isMaskToken());
    }

    public int maxSessionsPerUser() {
        return getInt("modality.session.max-sessions-per-user", properties.getMaxSessionsPerUser());
    }

    public int maxScan() {
        int value = getInt("modality.session.max-scan", properties.getMaxScan());
        return value <= 0 ? properties.getMaxScan() : value;
    }

    public long idleTimeoutSeconds() {
        return getLong("modality.session.idle-timeout-seconds", properties.getIdleTimeoutSeconds());
    }

    public long maxLifetimeSeconds() {
        return getLong("modality.session.max-lifetime-seconds", properties.getMaxLifetimeSeconds());
    }

    public int cleanupIntervalMinutes() {
        int value = getInt("modality.session.cleanup.interval-minutes",
                properties.getCleanup().getIntervalMinutes());
        return value <= 0 ? 1 : value;
    }

    public long zombieIdleSeconds() {
        return getLong("modality.session.cleanup.zombie-idle-seconds",
                properties.getCleanup().getZombieIdleSeconds());
    }

    public boolean cleanupAbsoluteTimeoutEnabled() {
        return properties.getCleanup().isAbsoluteTimeoutEnabled();
    }

    public long effectiveTtlSeconds(LoginType type) {
        long ttl = ttlSeconds();
        return ttl > 0 ? ttl : securityConfig.sessionTimeout(type);
    }

    public String truncatedMessage(int maxScan) {
        return "在线会话扫描已达上限 " + maxScan + " 条，结果可能不完整，请调大 modality.session.max-scan 或改用按账号精确查询";
    }
}
