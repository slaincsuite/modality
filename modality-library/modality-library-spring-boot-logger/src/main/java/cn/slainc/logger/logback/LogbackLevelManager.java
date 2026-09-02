package cn.slainc.logger.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.level.LogLevelManager;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LogbackLevelManager implements LogLevelManager {

    private final LoggerProperties.Level properties;

    private final ObjectProvider<DynamicConfigService> dynamicConfigService;

    private final Map<Long, Level> tenantLevels = new ConcurrentHashMap<>();

    public LogbackLevelManager(LoggerProperties.Level properties,
                               ObjectProvider<DynamicConfigService> dynamicConfigService) {
        this.properties = properties;
        this.dynamicConfigService = dynamicConfigService;
    }

    public static LoggerContext currentContext() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            return context;
        }
        return null;
    }

    public static Level parseLevel(String level) {
        return level == null || level.isBlank() ? null : Level.toLevel(level.trim().toUpperCase(Locale.ROOT), null);
    }

    @Override
    public boolean setLevel(String loggerName, String level) {
        LoggerContext context = currentContext();
        Level target = parseLevel(level);
        if (context == null || target == null || loggerName == null || loggerName.isBlank()) {
            return false;
        }
        context.getLogger(loggerName.trim()).setLevel(target);
        return true;
    }

    @Override
    public String getLevel(String loggerName) {
        LoggerContext context = currentContext();
        if (context == null) {
            return null;
        }
        Logger logger = context.getLogger(loggerName == null || loggerName.isBlank()
                ? Logger.ROOT_LOGGER_NAME : loggerName.trim());
        Level level = logger.getLevel();
        return level == null ? null : level.toString();
    }

    @Override
    public int refresh() {
        DynamicConfigService service = dynamicConfigService == null ? null : dynamicConfigService.getIfAvailable();
        if (service == null) {
            return 0;
        }
        int applied = 0;
        Map<String, String> levels = service.getGroup(properties.getConfigGroup());
        if (levels != null) {
            for (Map.Entry<String, String> entry : levels.entrySet()) {
                if (setLevel(entry.getKey(), entry.getValue())) {
                    applied++;
                }
            }
        }
        Map<String, String> tenants = service.getGroup(properties.getTenantConfigGroup());
        tenantLevels.clear();
        if (tenants != null) {
            for (Map.Entry<String, String> entry : tenants.entrySet()) {
                Level level = parseLevel(entry.getValue());
                Long tenantId = parseTenantId(entry.getKey());
                if (level != null && tenantId != null) {
                    tenantLevels.put(tenantId, level);
                    applied++;
                }
            }
        }
        return applied;
    }

    public Level levelForTenant(Long tenantId) {
        return tenantId == null ? null : tenantLevels.get(tenantId);
    }

    public Map<Long, Level> tenantLevels() {
        return new LinkedHashMap<>(tenantLevels);
    }

    private Long parseTenantId(String text) {
        try {
            return text == null ? null : Long.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
